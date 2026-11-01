import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Gives the static site clean URLs while keeping the pages as plain HTML.
 *
 * <p>Spring Boot serves the files from {@code classpath:/static/}; these
 * forwards let the navigation use {@code /docs} instead of
 * {@code /docs.html} without introducing a template engine.</p>
 */
@Controller
public class PageController {

    @GetMapping("/")
    public String home() {
        return "forward:/index.html";
    }

    @GetMapping("/home")
    public String homeAlias() {
        return "redirect:/";
    }

    @GetMapping({"/submit", "/submit/"})
    public String submit() {
        return "forward:/submit.html";
    }

    @GetMapping({"/docs", "/docs/"})
    public String docs() {
        return "forward:/docs.html";
    }

    @GetMapping({"/about", "/about/"})
    public String about() {
        return "forward:/about.html";
    }

    @GetMapping({"/privacy", "/privacy/"})
    public String privacy() {
        return "forward:/privacy.html";
    }

    @GetMapping({"/terms", "/terms/"})
    public String terms() {
        return "forward:/terms.html";
    }
}
