package servlet;

import annotations.Controller;
import annotations.RequestMapping;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@WebServlet(
        urlPatterns = "*.do",
        initParams = {@WebInitParam(name = "scanBasePackage", value = "controller", description = "扫描基础包")}
)
public class DispatchServlet extends HttpServlet {

    // controller缓存
    private Map<String, Object> controllers = new HashMap<>();

    // method缓存
    private Map<String, Method> methods = new HashMap<>();

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        try {
            // 参数配置：扫描controller包
            String scanBasePackage = config.getInitParameter("scanBasePackage");
            URL scanResource = Thread.currentThread().getContextClassLoader().getResource(scanBasePackage);
            if (scanResource.getProtocol().equals("file")) {
                File file = new File(scanResource.getFile());
                this.loadCache(file);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 加载缓存(不考虑子目录递归)：
     * controller
     * method
     *
     * @param file
     * @throws Exception
     */
    private void loadCache(File file) throws Exception {
        String replacePath = Thread.currentThread().getContextClassLoader().getResource("").getPath();
        if (file.isDirectory()) {
            for (File subFile : file.listFiles()) {
                String controllerPath = subFile.getCanonicalPath().replace(replacePath, "").replace(".class", "");
                Class<?> controllerClz = Class.forName(controllerPath.replace("/", "."));
                if (!controllerClz.isAnnotationPresent(RequestMapping.class) || !controllerClz.isAnnotationPresent(Controller.class)) {
                    continue;
                }
                String rmValue = controllerClz.getAnnotation(RequestMapping.class).value();
                controllers.put(rmValue, controllerClz.newInstance()); // 单例存储

                for (Method method : controllerClz.getMethods()) {
                    if (!method.isAnnotationPresent(RequestMapping.class)) {
                        continue;
                    }
                    String rmMethod = method.getAnnotation(RequestMapping.class).value();
                    methods.put(rmValue + rmMethod, method);
                }
            }
        }
    }

    // http://localhost:8080/custommvc/helloMvc/search.do
    // http://localhost:8080/custommvc/helloMvc/index.do
    // http://localhost:8080/custommvc/helloMvc/delete.do
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String contextPath = req.getContextPath();
        String requestURI = req.getRequestURI();
        requestURI = requestURI.replace(contextPath, "");

        // 确定controller
        Object controller = null;
        for (String controllerRm : controllers.keySet()) {
            if (!requestURI.startsWith(controllerRm + "/")) {
                continue;
            }
            controller = controllers.get(controllerRm);
            break;
        }
        if (controller == null) {
            throw new RuntimeException("404 not found controller exception!");
        }

        // 执行方法
        Method method = methods.get(requestURI.replace(".do", ""));
        try {
            Object result = method.invoke(controller);
            // 返回值输出这里可以用jackson将对象序列化成json字符串...
            resp.setContentType("text/html;charset=UTF-8");
            resp.getWriter().write(result.toString());
            resp.getWriter().flush();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void destroy() {
        super.destroy();
    }

}
