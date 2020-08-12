package custommvc.service;

import custommvc.annotations.Service;

@Service
public class HelloService {

    public void say(String info) {
        System.out.println(info);
    }
}
