import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HashController {

    @GetMapping("/hash")
    public String getHash(@RequestParam String input) {
        HashFunction.ToneParameters t = HashFunction.toneFor(input);
        return "{\"signature\":\"" + input + "\",\"hash\":\""
                + HashFunction.hashHex(input) + "\",\"tone\":" + t.toJson() + "}";
    }
}
