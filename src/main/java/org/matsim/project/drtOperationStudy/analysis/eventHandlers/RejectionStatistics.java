package org.matsim.project.drtOperationStudy.analysis.eventHandlers;

import org.matsim.contrib.dvrp.passenger.PassengerRequestRejectedEvent;
import org.matsim.contrib.dvrp.passenger.PassengerRequestRejectedEventHandler;

public class RejectionStatistics implements PassengerRequestRejectedEventHandler {
    private int rejectedRequests = 0;

    @Override
    public void handleEvent(PassengerRequestRejectedEvent passengerRequestRejectedEvent) {
        rejectedRequests++;
    }

    @Override
    public void reset(int iteration) {
        rejectedRequests = 0;
    }

    public int getRejectedRequests() {
        return rejectedRequests;
    }
}
