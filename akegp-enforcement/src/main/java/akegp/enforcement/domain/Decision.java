package akegp.enforcement.domain;

import java.time.Instant;

/** A reviewer's decision on a held pull request. */
public record Decision(DecisionAction action, String reviewer, Justification justification, String comment, Instant at) {}
