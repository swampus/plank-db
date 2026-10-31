package io.github.swampus.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OracleLabController {
    @GetMapping({"/oracle-lab", "/oracle-lab/"})
    public String index() { return "forward:/oracle-lab/index.html"; }
}
