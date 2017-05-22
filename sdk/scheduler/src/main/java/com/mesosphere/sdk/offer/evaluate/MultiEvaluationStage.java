package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.RangeAlgorithms;
import com.mesosphere.sdk.offer.ReserveOfferRecommendation;
import com.mesosphere.sdk.offer.ResourceBuilder;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This class evaluates an offer across zero or more child evaluation stages for a single resource type.
 */
public class MultiEvaluationStage implements OfferEvaluationStage {
    private final Collection<OfferEvaluationStage> childEvaluationStages;

    public MultiEvaluationStage(Collection<OfferEvaluationStage> childEvaluationStages) {
        this.childEvaluationStages = childEvaluationStages;
    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        List<EvaluationOutcome> childOutcomes = new ArrayList<>();
        List<OfferRecommendation> recommendations = new ArrayList<>();
        boolean allPassing = true;
        for (OfferEvaluationStage child : childEvaluationStages) {
            EvaluationOutcome originalOutcome = child.evaluate(mesosResourcePool, podInfoBuilder);
            // omit OfferRecommendation in child outcomes: don't duplicate our coalesced version
            childOutcomes.add(
                    EvaluationOutcome.create(originalOutcome.isPassing(), child, originalOutcome.getReason()));
            recommendations.addAll(originalOutcome.getOfferRecommendations());
            if (!originalOutcome.isPassing()) {
                allPassing = false;
            }
        }

        EvaluationOutcome outcome = EvaluationOutcome.create(
                allPassing, this, allPassing ? "All child stages passed" : "Failed to pass all child stages")
                .setChildren(childOutcomes);
        if (!recommendations.isEmpty()) {
            outcome.setOfferRecommendation(coalesceRangeRecommendations(recommendations));
        }
        return outcome;
    }

    private static OfferRecommendation coalesceRangeRecommendations(Collection<OfferRecommendation> recommendations) {
        Protos.Resource mergedResource = null;
        Protos.Offer offer = null;
        for (OfferRecommendation recommendation : recommendations) {
            Protos.Resource resource = recommendation.getOperation().getReserve().getResources(0);
            if (offer == null) {
                offer = recommendation.getOffer();
            }

            if (mergedResource == null) {
                mergedResource = resource;
            } else {
                Value.Builder mergedValue = Value.newBuilder();
                mergedValue
                        .setType(Value.Type.RANGES)
                        .getRangesBuilder().addAllRange(RangeAlgorithms.mergeRanges(
                                mergedResource.getRanges().getRangeList(),
                                resource.getRanges().getRangeList()));
                mergedResource = ResourceBuilder.fromExistingResource(mergedResource)
                        .setValue(mergedValue.build())
                        .build();
            }
        }

        return new ReserveOfferRecommendation(offer, mergedResource);
    }
}
