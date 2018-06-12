package com.zzf.myspringmvc.servlet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.zzf.myspringmvc.annotaion.MyAutowired;
import com.zzf.myspringmvc.annotaion.MyController;
import com.zzf.myspringmvc.annotaion.MyRequestMapping;
import com.zzf.myspringmvc.annotaion.MyRequestParam;
import com.zzf.myspringmvc.annotaion.MyService;

public class MySpringMVCServlet extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private Properties p = new Properties();

	private List<String> classNames = new ArrayList<String>();

	private Map<String, Object> ioc = new HashMap<String, Object>();

	private List<Handler> handlerMapping = new ArrayList<Handler>();

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		this.doPost(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			doDispatch(req, resp);
		} catch (Exception e) {
			resp.getWriter().write("500 Exception , deails : \r\n" + Arrays.toString(e.getStackTrace()));
		}
	}

	@Override
	public void init(ServletConfig config) throws ServletException {
		System.out.println("------------------------");
		// 1.加载配置文件
		loadconfig(config.getInitParameter("contextConfigLocation"));
		// 2、初始化所有相关联的类扫描
		doScanner(p.getProperty("scanPackage"));
		// 3、将所有相关类的实例初始化，并将其保存到IOC容器中
		doInstance();
		// 4、自动化的依赖注入
		doAutowired();
		// 5、初始化HandlerMapping
		initHandlerMapping();
		System.out.println("zzf mvc init success !");
	}

	private void loadconfig(String location) {
		InputStream is = this.getClass().getClassLoader().getResourceAsStream(location);
		try {
			p.load(is);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (null != is) {
					is.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void doScanner(String packageName) {
		URL url = this.getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.", "/"));
		File calssDir = new File(url.getFile());
		for (File file : calssDir.listFiles()) {
			if (file.isDirectory()) {
				doScanner(packageName + "." + file.getName());
			} else {
				classNames.add(packageName + "." + file.getName().replace(".class", ""));
			}
		}
	}

	private void doInstance() {
		if (classNames.isEmpty()) {
			return;
		}
		try {
			for (String className : classNames) {
				Class<?> clazz = Class.forName(className);
				// 进行实例化，原则问题（用反射来实例化）
				// 判断，不是所有的类都是要实例化（只处理MyController和MyService）
				if (clazz.isAnnotationPresent(MyController.class)) {
					String beanName = lowerFirst(clazz.getSimpleName());
					ioc.put(beanName, clazz.newInstance());
				} else if (clazz.isAnnotationPresent(MyService.class)) {
					MyService service = clazz.getAnnotation(MyService.class);
					// 如果自己定义了名字的话，优先用自己定义的名字
					String beanName = service.value();
					if ("".equals(beanName.trim())) {
						// 如果自己没设置名字，默认采用类名的首字母小写
						beanName = lowerFirst(clazz.getSimpleName());
					}
					Object instance = clazz.newInstance();
					ioc.put(beanName, instance);
					// 如果@MyAutowired标注的是一个接口的话，默认要将其实现的类的实例注入进来
					Class<?>[] interfaces = clazz.getInterfaces();
					for (Class<?> i : interfaces) {
						ioc.put(i.getName(), instance);
					}
				} else {
					continue;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void doAutowired() {
		if (ioc.isEmpty()) {
			return;
		}
		for (Entry<String, Object> entry : ioc.entrySet()) {
			// 在Spring里面没有隐私
			// 扫描所有的字段，看有没有加@MyAutowired的注解
			Field[] fields = entry.getValue().getClass().getDeclaredFields();
			for (Field field : fields) {
				if (!field.isAnnotationPresent(MyAutowired.class)) {
					continue;
				}

				MyAutowired autowired = field.getAnnotation(MyAutowired.class);
				String beanName = autowired.value().trim();
				if ("".equals(beanName)) {
					beanName = field.getType().getName();
				}
				field.setAccessible(true);
				try {
					field.set(entry.getValue(), ioc.get(beanName));
				} catch (IllegalArgumentException e) {
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void initHandlerMapping() {
		if (ioc.isEmpty()) {
			return;
		}
		for (Entry<String, Object> entry : ioc.entrySet()) {
			Class<?> clazz = entry.getValue().getClass();
			if (!clazz.isAnnotationPresent(MyController.class)) {
				continue;
			}
			String baseUrl = "";
			if (clazz.isAnnotationPresent(MyRequestMapping.class)) {
				MyRequestMapping mapping = clazz.getAnnotation(MyRequestMapping.class);
				baseUrl = mapping.value();
			}
			Method[] Methods = clazz.getMethods();
			for (Method method : Methods) {
				if (!method.isAnnotationPresent(MyRequestMapping.class)) {
					continue;
				}
				MyRequestMapping mapping = method.getAnnotation(MyRequestMapping.class);
				// String url = mapping.value();
				// url = (baseUrl + "/" + url).replaceAll("/+", "/");
				// handlerMapping.put(url, method);
				// System.out.println("Mapping : " + url + "," + method);

				String regex = ("/" + baseUrl + mapping.value()).replaceAll("/+", "/");
				Pattern pattern = Pattern.compile(regex);
				handlerMapping.add(new Handler(pattern, entry.getValue(), method));
				System.out.println("Mapping : " + regex + "," + method);
			}
		}
	}

	private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
		try {
			Handler handler = getHandler(req);
			if (handler == null) {
				resp.getWriter().write("404 not Found!");
				return;
			}
			Class<?>[] paramTypes = handler.method.getParameterTypes();
			Object[] paramValues = new Object[paramTypes.length];

			Map<String, String[]> params = req.getParameterMap();
			for (Entry<String, String[]> param : params.entrySet()) {
				System.out.println("param : " + param.getValue());
				String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");
				if (!handler.paramIndexMapping.containsKey(param.getKey())) {
					continue;
				}
				int index = handler.paramIndexMapping.get(param.getKey());
				paramValues[index] = convert(paramTypes[index], value);
			}
			int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
			paramValues[reqIndex] = req;
			int respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
			paramValues[respIndex] = resp;

			handler.method.invoke(handler.controller, paramValues);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private Object convert(Class<?> type, String value) {
		if (Integer.class == type) {
			return Integer.valueOf(value);
		}
		return value;
	}

	private Handler getHandler(HttpServletRequest req) throws Exception {
		if (handlerMapping.isEmpty()) {
			return null;
		}
		String url = req.getRequestURI();
		String contextPath = req.getContextPath();
		url = url.replace(contextPath, "").replaceAll("/+", "/");
		for (Handler handler : handlerMapping) {
			try {
				Matcher matcher = handler.pattern.matcher(url);
				if (!matcher.matches()) {
					continue;
				}
				return handler;
			} catch (Exception e) {
				throw e;
			}
		}
		return null;
	}

	private String lowerFirst(String str) {
		char[] chars = str.toCharArray();
		chars[0] += 32;
		return String.valueOf(chars);
	}

	/**
	 * Handler记录Controller中的RequestMapping和Method的对应关系
	 * @author zhangzengfu
	 *
	 */
	private class Handler {
		protected Object controller;// 保存方法对应的实例
		protected Method method;
		protected Pattern pattern;
		protected Map<String, Integer> paramIndexMapping;

		protected Handler(Pattern pattern, Object controller, Method method) {
			this.pattern = pattern;
			this.controller = controller;
			this.method = method;
			paramIndexMapping = new HashMap<String, Integer>();
			putParamIndexMapping(method);
		}

		private void putParamIndexMapping(Method method) {
			Annotation[][] pa = method.getParameterAnnotations();
			for (int i = 0; i < pa.length; i++) {
				for (Annotation a : pa[i]) {
					if (a instanceof MyRequestParam) {
						String paramName = ((MyRequestParam) a).value();
						if (!"".equals(paramName.trim())) {
							paramIndexMapping.put(paramName, i);
						}
					}
				}
			}
			Class<?>[] paramsTypes = method.getParameterTypes();
			for (int i = 0; i < paramsTypes.length; i++) {
				Class<?> type = paramsTypes[i];
				if (type == HttpServletRequest.class || type == HttpServletResponse.class) {
					paramIndexMapping.put(type.getName(), i);
				}
			}
		}

	}
}
