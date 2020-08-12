package custommvc.controller;

import custommvc.annotations.Autowired;
import custommvc.annotations.Controller;
import custommvc.annotations.RequestMapping;
import custommvc.annotations.RequestParam;
import custommvc.service.HelloService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
@RequestMapping("/helloMvc")
public class HelloMvcController {

    @Autowired("helloService")
    private HelloService helloService;

    // http://localhost:8080/custommvc/helloMvc/index.do?name=aaaaa
    @RequestMapping("/index")
    public String index(HttpServletRequest request, HttpServletResponse response, @RequestParam("name")String name) {
        helloService.say(name);
        return "你访问了/helloMvc/index";
    }

    // http://localhost:8080/custommvc/helloMvc/search.do
    @RequestMapping("/search")
    public String search() {
        helloService.say("service: 你访问了/helloMvc/search");
        return "你访问了/helloMvc/search";
    }

    @RequestMapping("/delete")
    public String delete() {
        helloService.say("service: 你访问了/helloMvc/delete");
        return "你访问了/helloMvc/delete";
    }

}
