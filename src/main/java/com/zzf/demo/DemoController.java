package com.zzf.demo;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.zzf.myspringmvc.annotaion.MyAutowired;
import com.zzf.myspringmvc.annotaion.MyController;
import com.zzf.myspringmvc.annotaion.MyRequestMapping;
import com.zzf.myspringmvc.annotaion.MyRequestParam;

@MyController
@MyRequestMapping("/demo")
public class DemoController {

	@MyAutowired
	private IDemoService demoService;

	@MyRequestMapping("/query.json")
	public void query(HttpServletRequest req, HttpServletResponse resp, @MyRequestParam("name") String name) {
		String result = demoService.get(name);
		try {
			resp.getWriter().write(result);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
