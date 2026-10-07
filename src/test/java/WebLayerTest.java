import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
}
