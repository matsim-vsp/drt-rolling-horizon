package org.matsim.project.drtOperationStudy.run.modules;

import org.matsim.contrib.drt.optimizer.insertion.IncrementalStopDurationEstimator;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.schedule.StopDurationEstimator;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeModule;
import org.matsim.project.utils.LinearDrtStopDurationEstimator;

public class LinearStopDurationModule extends AbstractDvrpModeModule {

    private final DrtConfigGroup drtConfigGroup;

    public LinearStopDurationModule(DrtConfigGroup drtConfigGroup) {
        super(drtConfigGroup.mode);
        this.drtConfigGroup = drtConfigGroup;
    }

    @Override
    public void install() {
        bindModal(StopDurationEstimator.class).toInstance((vehicle, dropoffRequests, pickupRequests) -> drtConfigGroup.stopDuration * (dropoffRequests.size() + pickupRequests.size()));
        bindModal(IncrementalStopDurationEstimator.class).toInstance(new LinearDrtStopDurationEstimator(drtConfigGroup.stopDuration));
    }
}
