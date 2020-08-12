package custommvc.servlet;

import custommvc.annotations.Autowired;
import custommvc.annotations.Controller;
import custommvc.annotations.RequestMapping;
import custommvc.annotations.Service;
import custommvc.handler.Handler;
import custommvc.utils.BeanUtil;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@WebServlet(
        urlPatterns = "*.do",
        initParams = {@WebInitParam(name = "scanPackage", value = "custommvc", description = "扫描基础包")}
)
public class DispatchServlet extends HttpServlet {

    private final List<String> classNames = new ArrayList<>();

    private final Map<String, Object> ioc = new HashMap<>();

    private final List<Handler> handlerMapping = new ArrayList<>();

    @Override
    public void init(ServletConfig config) throws ServletException {
            // 参数配置：扫描controller包
            String scanPackage = config.getInitParameter("scanPackage");

            //scan relative class
            this.doScanner(scanPackage);
            //init ioc container put relative class to it
            this.doInstance();
            //inject dependence
            this.doAutoWired();
            //init handlerMapping
            this.initHandlerMapping();
    }

    private void doScanner(String packageName) {
        URL resource = this.getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.", "/"));
        File classDir = new File(resource.getFile());
        for (File classFile : classDir.listFiles()) {
            if (classFile.isDirectory()) {
                this.doScanner(packageName + "." + classFile.getName());
            } else {
                String className = (packageName + "." + classFile.getName()).replace(".class", "");
                classNames.add(className);
            }
        }
    }

    private void doInstance() {
        if (classNames.isEmpty()) return;
        try {
            for (String className : classNames) {
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(Controller.class)) {
                    String beanName = BeanUtil.lowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName, clazz.newInstance());
                } else if (clazz.isAnnotationPresent(Service.class)) {
                    Service service = clazz.getAnnotation(Service.class);
                    String beanName = service.value();
                    if ("".equals(beanName)) {
                        beanName = BeanUtil.lowerFirstCase(clazz.getSimpleName());
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class<?> i : interfaces) {
                        ioc.put(i.getName(), instance);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void doAutoWired() {
        if (ioc.isEmpty()) return;
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            //依赖注入->给加了XXAutowired注解的字段赋值
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (!field.isAnnotationPresent(Autowired.class)) {
                    continue;
                }
                Autowired autowired = field.getAnnotation(Autowired.class);
                String beanName = autowired.value();
                if ("".equals(beanName)) {
                    beanName = field.getType().getName();
                }
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    continue;
                }
            }
        }
    }

    private void initHandlerMapping() {
        if (ioc.isEmpty()) return;
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(Controller.class)) {
                continue;
            }
            String baseUrl = "";
            if (clazz.isAnnotationPresent(RequestMapping.class)) {
                RequestMapping requestMapping = clazz.getAnnotation(RequestMapping.class);
                baseUrl = requestMapping.value();
            }
            Method[] methods = clazz.getMethods();
            for (Method method : methods) {
                if (!method.isAnnotationPresent(RequestMapping.class)) {
                    continue;
                }
                RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
                String url = (baseUrl + requestMapping.value()).replaceAll("/+", "/");
                Pattern pattern = Pattern.compile(url);
                handlerMapping.add(new Handler(pattern, entry.getValue(), method));
                System.out.println("mapped:" + url + "=>" + method);
            }
        }
    }

    // http://localhost:8080/custommvc/helloMvc/search.do
    // http://localhost:8080/custommvc/helloMvc/index.do
    // http://localhost:8080/custommvc/helloMvc/delete.do
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doDispatcher(req, resp);
    }

    public void doDispatcher(HttpServletRequest req, HttpServletResponse res) {
        try {
            Handler handler = getHandler(req);
            if (handler == null) {
                res.getWriter().write("404 not found.");
                return;
            }
            Class<?>[] paramTypes = handler.getMethod().getParameterTypes();
            Object[] paramValues = new Object[paramTypes.length];
            Map<String, String[]> params = req.getParameterMap();
            for (Map.Entry<String, String[]> param : params.entrySet()) {
                String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "");
                if (!handler.getParamIndexMapping().containsKey(param.getKey())) {
                    continue;
                }
                int index = handler.getParamIndexMapping().get(param.getKey());
                paramValues[index] = this.convert(paramTypes[index], value);
            }
            if (handler.getParamIndexMapping().get(HttpServletRequest.class.getName()) != null) {
                int reqIndex = handler.getParamIndexMapping().get(HttpServletRequest.class.getName());
                paramValues[reqIndex] = req;
            }
            if (handler.getParamIndexMapping().get(HttpServletResponse.class.getName()) != null) {
                int resIndex = handler.getParamIndexMapping().get(HttpServletResponse.class.getName());
                paramValues[resIndex] = res;
            }
            Object result = handler.getMethod().invoke(handler.getController(), paramValues);
            if (result != null) {
                // RestController返回
                // 返回值输出这里可以用jackson将对象序列化成json字符串...
                res.setContentType("text/html;charset=UTF-8");
                res.getWriter().write(result.toString());
                res.getWriter().flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
//        String url = req.getRequestURI();
//        String contextPath = req.getContextPath();
//        url = url.replace(contextPath, "").replaceAll("/+", "/");
    }

    private Object convert(Class<?> type, String value) {
        if (Integer.class == type) {
            return Integer.valueOf(value);
        }
        return value;
    }

    private Handler getHandler(HttpServletRequest req) {
        if (handlerMapping.isEmpty()) {
            return null;
        }
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "").replace(".do", "").replaceAll("/+", "/");
        for (Handler handler : handlerMapping) {
            Matcher matcher = handler.getPattern().matcher(url);
            if (!matcher.matches()) {
                continue;
            }
            return handler;
        }
        return null;
    }

}
