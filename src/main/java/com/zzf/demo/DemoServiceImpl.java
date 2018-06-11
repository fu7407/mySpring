package com.zzf.demo;

import com.zzf.myspringmvc.annotaion.MyService;

@MyService
public class DemoServiceImpl implements IDemoService {

	@Override
	public String get(String name) {
		return "My name is " + name;
	}

}
