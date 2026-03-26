package edu.fisa.ce;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {
	
	//http://localhost/ce1
	@GetMapping("ce1")
	public String getData() {
		System.out.println("요청 및 응답 -------------------");
		return "응답 데이터";
	}
}
