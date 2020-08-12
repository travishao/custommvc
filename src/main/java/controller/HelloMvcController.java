package controller;

import annotations.Controller;
import annotations.RequestMapping;

@Controller
@RequestMapping("/helloMvc")
public class HelloMvcController {

    @RequestMapping("/index")
    public String index() {
        return "你访问了/helloMvc/index";
    }

    @RequestMapping("/search")
    public String search() {
        return "你访问了/helloMvc/search";
    }

    @RequestMapping("/delete")
    public String delete() {
        return "你访问了/helloMvc/delete";
    }

}
