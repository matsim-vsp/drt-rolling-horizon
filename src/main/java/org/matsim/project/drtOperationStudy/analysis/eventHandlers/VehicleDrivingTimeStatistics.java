package org.matsim.project.drtOperationStudy.analysis.eventHandlers;

import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.events.handler.VehicleEntersTrafficEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleLeavesTrafficEventHandler;

/**
 * Read total fleet driving time from the event file
 */
public class VehicleDrivingTimeStatistics implements VehicleEntersTrafficEventHandler,
        VehicleLeavesTrafficEventHandler {
    private double totalDrivingTime;

    @Override
    public void reset(int iteration) {
        totalDrivingTime = 0;
    }

    @Override
    public void handleEvent(VehicleEntersTrafficEvent vehicleEntersTrafficEvent) {
        double enterTime = vehicleEntersTrafficEvent.getTime();
        totalDrivingTime -= enterTime;
    }

    @Override
    public void handleEvent(VehicleLeavesTrafficEvent vehicleLeavesTrafficEvent) {
        double leavingTime = vehicleLeavesTrafficEvent.getTime();
        totalDrivingTime += leavingTime;
    }

    public double getTotalDrivingTime() {
        return totalDrivingTime;
    }
}
