import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@RestController
public class HashController {

    private static final ObjectMapper M = new ObjectMapper();

    @GetMapping("/hash")
    public ResponseEntity<String> getHash(@RequestParam String input) throws JacksonException {
        var node = M.createObjectNode();
        node.put("signature", input);
        node.put("hash", HashFunction.hashHex(input));
        node.set("tone", M.readTree(HashFunction.toneFor(input).toJson()));
        return ResponseEntity.ok(M.writeValueAsString(node));
    }
}
