package com.vainolo.phd.opm.gef.editor.policy;

import org.eclipse.gef.Request;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.editpolicies.GraphicalEditPolicy;

import com.vainolo.phd.opm.gef.editor.action.ThingInZoomAction;
import com.vainolo.phd.opm.gef.editor.action.ToggleThingMultiplicityAction;
import com.vainolo.phd.opm.gef.editor.command.OPMToggleThingMultiplicityCommand;
import com.vainolo.phd.opm.gef.editor.command.OPMThingInZoomCommand;
import com.vainolo.phd.opm.gef.editor.part.OPMThingEditPart;

public class OPMThingEditPolicy extends GraphicalEditPolicy {
  public static final String ID = "OPMThingEditPolicy";

  private Command thingInZoom() {
    OPMThingEditPart part = (OPMThingEditPart) getHost();
    OPMThingInZoomCommand command = new OPMThingInZoomCommand(part);
    return command;
  }

  private Command toggleMultiplicity() {
    OPMThingEditPart part = (OPMThingEditPart) getHost();
    OPMToggleThingMultiplicityCommand command = new OPMToggleThingMultiplicityCommand(part);
    return command;
  }

  @Override
  public Command getCommand(Request request) {
    if(request.getType().equals(ThingInZoomAction.THING_IN_ZOOM_REQUEST)) {
      return thingInZoom();
    } else if(request.getType().equals(ToggleThingMultiplicityAction.TOGGLE_MULTIPLICITY_REQUEST)) {
      return toggleMultiplicity();
    } else {
      return super.getCommand(request);
    }
  }

}
