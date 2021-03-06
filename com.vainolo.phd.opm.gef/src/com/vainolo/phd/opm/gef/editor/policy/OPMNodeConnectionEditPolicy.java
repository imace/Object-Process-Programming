/*******************************************************************************
 * Copyright (c) 2012 Arieh 'Vainolo' Bibliowicz
 * You can use this code for educational purposes. For any other uses
 * please contact me: vainolo@gmail.com
 *******************************************************************************/

package com.vainolo.phd.opm.gef.editor.policy;

import org.eclipse.draw2d.geometry.Dimension;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CompoundCommand;
import org.eclipse.gef.commands.UnexecutableCommand;
import org.eclipse.gef.editpolicies.GraphicalNodeEditPolicy;
import org.eclipse.gef.requests.CreateConnectionRequest;
import org.eclipse.gef.requests.ReconnectRequest;

import com.google.common.base.Preconditions;
import com.vainolo.phd.opm.gef.editor.command.OPMLinkCreateCommand;
import com.vainolo.phd.opm.gef.editor.command.OPMNodeCreateCommand;
import com.vainolo.phd.opm.gef.editor.factory.OPMIdManager;
import com.vainolo.phd.opm.gef.editor.factory.OPMStructuralLinkFactory;
import com.vainolo.phd.opm.gef.editor.part.OPMStructuralLinkAggregatorEditPart;
import com.vainolo.phd.opm.model.*;
import com.vainolo.phd.opm.utilities.analysis.OPDAnalyzer;
import com.vainolo.phd.opm.validation.OPMLinkValidator;

/**
 * Policy used to connect two nodes in the diagram. Currently connections can
 * only be created between two {@link OPMThing} instances.
 * 
 * @author vainolo
 */
public class OPMNodeConnectionEditPolicy extends GraphicalNodeEditPolicy {

  private static final Dimension DEFAULT_AGGREGATOR_DIMENSION = new Dimension(15, 15);
  OPMLinkValidator validator;
  OPDAnalyzer analyzer;

  OPMStructuralLinkFactory linkFactory;

  public OPMNodeConnectionEditPolicy(OPMLinkValidator validator, OPDAnalyzer analyzer, OPMIdManager idManager) {
    Preconditions.checkNotNull(validator);
    this.validator = validator;
    this.analyzer = analyzer;
    this.linkFactory = new OPMStructuralLinkFactory(idManager);
  }

  /**
   * Create a command used to begin connecting to nodes.
   * {@link OPMStructuralLinkAggregatorEditPart} nodes cannot be source nodes,
   * therefore in this case a {@link UnexecutableCommand} is returned.
   * 
   * @return a {@link Command} that contains the initial information neede to
   *         create a connection between two nodes.
   */
  @Override
  protected Command getConnectionCreateCommand(CreateConnectionRequest request) {
    // We must return null and not the usual UnexecutableCommand because if we
    // return a non-null value the framework thinks that the link can be created
    // from this host, something that we don't want to happen.
    if(getHost() instanceof OPMStructuralLinkAggregatorEditPart) {
      return null;
    }

    if(request.getNewObject() instanceof OPMStructuralLinkAggregator) {
      request.setStartCommand(new Command() {
      });
      return request.getStartCommand();
    }

    if(!validator.validateAddSource((OPMNode) getHost().getModel(), (OPMLink) request.getNewObject())) {
      return null;
    }

    OPMLinkCreateCommand result = new OPMLinkCreateCommand();
    result.setSource((OPMNode) getHost().getModel());
    result.setLink((OPMLink) request.getNewObject());
    result.setOPD(analyzer.findOPD((OPMNode) (OPMNode) getHost().getModel()));
    request.setStartCommand(result);
    return result;
  }

  /**
   * Retrieves the command created by
   * {@link OPMNodeConnectionEditPolicy#getConnectionCreateCommand(CreateConnectionRequest)
   * getConnectionCreateCommand}, and adds it information so that the command
   * can be executed. {@link OPMStructuralLinkAggregatorEditPart} nodes cannot
   * be source nodes, therefore in this case a {@link UnexecutableCommand} is
   * returned.
   * 
   * @return a {@link Command} that can be executed to create a connection
   *         between two nodes.
   */
  @Override
  protected Command getConnectionCompleteCommand(CreateConnectionRequest request) {
    // A null command must be returned (and not the usual UnexecutableCommand),
    // otherwise GEF shows the used a symbol that the connection can be
    // completed but when the used clicks it is not created.
    if(request.getStartCommand() == null || request.getTargetEditPart() instanceof OPMStructuralLinkAggregatorEditPart) {
      return null;
    }

    Command command = null;

    if(request.getNewObject() instanceof OPMStructuralLinkAggregator) {
      command = handleOPMStructuralLinkRequest(request);
    } else {
      if(!validator.validateAddTarget((OPMLink) request.getNewObject(), (OPMNode) getHost().getModel())) {
        return null;
      }

      OPMLinkCreateCommand linkCreateCommand = (OPMLinkCreateCommand) request.getStartCommand();
      linkCreateCommand.setTarget((OPMNode) getHost().getModel());
      command = linkCreateCommand;
    }

    return command;
  }

  /**
   * <p>
   * When the user requests the creation of a structural link, the following is
   * done:
   * </p>
   * <ol>
   * <li>If this is the first structural link of its kind between the source and
   * target nodes, we create a new aggregator and connect it to the source and
   * target.</li>
   * <li>If there already is an aggregator of its kind between the nodes, we
   * only add a new link from the aggregator to the new target.</li>
   * </ol>
   * 
   * @param request
   *          the user request to create a new strucutral link between the
   *          nodes.
   * @return a command that creates the links as stated above.
   */
  private Command handleOPMStructuralLinkRequest(CreateConnectionRequest request) {
    Command command = null;

    OPMNode sNode = (OPMNode) request.getSourceEditPart().getModel();
    OPMNode tNode = (OPMNode) request.getTargetEditPart().getModel();
    OPMStructuralLinkAggregator agrNode = (OPMStructuralLinkAggregator) request.getNewObject();

    // Search for an outgoing structural link aggregator matching the
    // requested kind.
    boolean aggregatorFound = false;
    for(OPMLink structuralLink : analyzer.findOutgoingStructuralLinks(sNode)) {
      OPMStructuralLinkAggregator existingAggregator = (OPMStructuralLinkAggregator) structuralLink.getTarget();
      if(existingAggregator.getKind() == agrNode.getKind()) {
        aggregatorFound = true;
        agrNode = existingAggregator;
      }
    }

    if(aggregatorFound) {
      // Just create a link from the aggregator to the target.
      command = createCreateOPMLlinkCreateCommand(agrNode, tNode, analyzer.findOPD(agrNode));
    } else {
      // Create a compound command consisting of three commands.
      CompoundCommand cCommand = new CompoundCommand();
      cCommand.add(createCreateAggregatorNodeCommand(sNode, tNode, agrNode));
      cCommand.add(createCreateOPMLlinkCreateCommand(sNode, agrNode, analyzer.findOPD(sNode)));
      cCommand.add(createCreateOPMLlinkCreateCommand(agrNode, tNode, analyzer.findOPD(sNode)));

      command = cCommand;
    }

    return command;
  }

  /**
   * Helper function to create a command that connects two nodes with a factory
   * generated link.
   * 
   * @param source
   *          the source of the link.
   * @param target
   *          the target of the link.
   * @return
   */
  private OPMLinkCreateCommand createCreateOPMLlinkCreateCommand(OPMNode source, OPMNode target,
      OPMObjectProcessDiagram opd) {
    OPMLinkCreateCommand command = new OPMLinkCreateCommand();
    command.setSource(source);
    command.setTarget(target);
    command.setOPD(opd);
    OPMStructuralLink link = linkFactory.getNewObject();
    command.setLink(link);
    return command;
  }

  /**
   * Create a command that adds the provided {@link OPMStructuralLinkAggregator}
   * to the diagram located between the source and the target {@link OPMNode}.
   * 
   * @param source
   *          the source for the structural link.
   * @param target
   *          the target of the structural link.
   * @param aggregator
   *          the aggregator that should be added to the diagram.
   * @return A {@link OPMNodeCreateCommand} whose execution add the aggregator
   *         to the diagram.
   */
  public OPMNodeCreateCommand createCreateAggregatorNodeCommand(OPMNode source, OPMNode target, OPMNode aggregator) {
    OPMNodeCreateCommand command = new OPMNodeCreateCommand();
    command.setNode(aggregator);
    command.setContainer(source.getContainer());

    // Calculate location of aggregator, between the source and targetnodes.
    Rectangle sCnstrnts = source.getConstraints();
    Rectangle tCnstrnts = target.getConstraints();
    Point sCenter = new Point(sCnstrnts.x + sCnstrnts.width / 2, sCnstrnts.y + sCnstrnts.height / 2);
    Point tCenter = new Point(tCnstrnts.x + tCnstrnts.width / 2, tCnstrnts.y + tCnstrnts.height / 2);
    Point aggrgLeftTopCorner = new Point();
    aggrgLeftTopCorner.x = sCenter.x + (tCenter.x - sCenter.x) / 2 - DEFAULT_AGGREGATOR_DIMENSION.width / 2;
    aggrgLeftTopCorner.y = sCenter.y + (tCenter.y - sCenter.y) / 2 - DEFAULT_AGGREGATOR_DIMENSION.height / 2;
    if(aggrgLeftTopCorner.x < 0) {
      aggrgLeftTopCorner.x = 0;
    }
    if(aggrgLeftTopCorner.y < 0) {
      aggrgLeftTopCorner.y = 0;
    }
    command.setConstraints(new Rectangle(aggrgLeftTopCorner, DEFAULT_AGGREGATOR_DIMENSION));

    return command;
  }

  @Override
  protected Command getReconnectTargetCommand(ReconnectRequest request) {
    return null;
  }

  @Override
  protected Command getReconnectSourceCommand(ReconnectRequest request) {
    return null;
  }
}