package akegp.rules.domain;

public enum RelationshipType {
  CALLS,
  DEPENDS_ON,
  DIRECT_DATABASE_ACCESS,
  DATA_ACCESS,
  PUBLISHES_TO,
  CONSUMES_FROM,
  USES_API
}
