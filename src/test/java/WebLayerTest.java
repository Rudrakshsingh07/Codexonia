import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = CodexoniaApplication.class)
@AutoConfigureMockMvc
class WebLayerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void hashEndpointReturnsToneJson() throws Exception {
        mvc.perform(get("/hash").param("input", "swap"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.signature").value("swap"))
           .andExpect(jsonPath("$.hash").exists())
           .andExpect(jsonPath("$.tone.frequencyHz").exists());
    }

    @Test
    void runRejectsBlankSource() throws Exception {
        mvc.perform(post("/api/run")
                .contentType("application/json")
                .content("{\"source\":\"  \"}"))
           .andExpect(status().isBadRequest());
    }

    @Test
    void runWithoutMainReturnsClassifiedJson() throws Exception {
        mvc.perform(post("/api/run")
                .contentType("application/json")
                .content("{\"source\":\"public class NoMain { }\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.status").value("NO_MAIN_METHOD"))
           .andExpect(jsonPath("$.error_message").exists())
           .andExpect(jsonPath("$.event_count").value(0))
           .andExpect(jsonPath("$.timeline.frame_count").value(0))
           .andExpect(jsonPath("$.timeline.error_message").exists())
           .andExpect(jsonPath("$.timeline.sources[0].file_name").value("NoMain.java"));
    }

    @Test
    void cleanUrlsForwardToStaticPages() throws Exception {
        assertForward("/", "/index.html");
        assertForward("/submit", "/submit.html");
        assertForward("/submit/", "/submit.html");
        assertForward("/docs", "/docs.html");
        assertForward("/docs/", "/docs.html");
        assertForward("/about", "/about.html");
        assertForward("/privacy", "/privacy.html");
        assertForward("/terms", "/terms.html");
    }

    @Test
    void homeAliasRedirectsToRoot() throws Exception {
        mvc.perform(get("/home"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/"));
    }

    @Test
    void sharedAssetsAreServed() throws Exception {
        mvc.perform(get("/assets/site.css"))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith("text/css"));
        mvc.perform(get("/assets/site.js"))
           .andExpect(status().isOk());
        mvc.perform(get("/assets/codexonia-mark.svg"))
           .andExpect(status().isOk());
        mvc.perform(get("/favicon.ico"))
           .andExpect(status().isOk());
    }

    @Test
    void errorPagesAreServed() throws Exception {
        mvc.perform(get("/error/404.html"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("The trace ends here.")));
        mvc.perform(get("/error/500.html"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("The recorder hit a bad frame.")));
    }

    @Test
    void unknownUrlReturnsNotFound() throws Exception {
        mvc.perform(get("/this-page-does-not-exist"))
           .andExpect(status().isNotFound());
    }

    private void assertForward(String path, String target) throws Exception {
        mvc.perform(get(path))
           .andExpect(status().isOk())
           .andExpect(forwardedUrl(target));
    }
}
