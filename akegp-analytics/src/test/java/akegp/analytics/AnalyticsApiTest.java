package akegp.analytics;

import akegp.analytics.api.AnalyticsController;
import akegp.analytics.service.AnalyticsQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AnalyticsApiTest {
  @Test void apiUsesEngineAndPreservesUnavailableValues() throws Exception {
    var service=new AnalyticsQueryService(p->AnalyticsEnginesTest.demo(),(principal,p)->{});
    var mvc=MockMvcBuilders.standaloneSetup(new AnalyticsController(service)).build();
    String base="/api/analytics/projects/"+AnalyticsEnginesTest.PROJECT;
    mvc.perform(get(base+"/runs")).andExpect(status().isOk()).andExpect(jsonPath("$[0].label").value("Demo snapshot - not upstream analysis"));
    mvc.perform(get(base+"/comparisons").param("currentRunId",AnalyticsEnginesTest.demo().getFirst().id().toString()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.baseline").value(true)).andExpect(jsonPath("$.newCount").isEmpty());
    mvc.perform(get(base+"/hotspots").param("currentRunId",AnalyticsEnginesTest.demo().getLast().id().toString()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].entityId").value("Payment"));
    mvc.perform(get(base+"/comparisons").param("currentRunId",java.util.UUID.randomUUID().toString()))
        .andExpect(status().isBadRequest());
  }
  @Test void authorizationAndStorageFailuresAreSafe() throws Exception {
    var denied=MockMvcBuilders.standaloneSetup(new AnalyticsController(new AnalyticsQueryService(p->{throw new AssertionError();},
        (principal,p)->{throw new SecurityException("sensitive details");}))).build();
    String path="/api/analytics/projects/"+AnalyticsEnginesTest.PROJECT+"/runs";
    denied.perform(get(path)).andExpect(status().isForbidden()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sensitive"))));
    var failed=MockMvcBuilders.standaloneSetup(new AnalyticsController(new AnalyticsQueryService(p->{throw new IllegalStateException("secret url");},(principal,p)->{}))).build();
    failed.perform(get(path)).andExpect(status().isServiceUnavailable()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
  }
}
