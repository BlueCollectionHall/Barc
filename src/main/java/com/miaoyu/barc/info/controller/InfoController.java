package com.miaoyu.barc.info.controller;

import com.miaoyu.barc.response.SuccessR;
import com.miaoyu.barc.utils.AppService;
import com.miaoyu.barc.utils.J;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.util.Date;

@RestController
@RequestMapping("/info")
public class InfoController {
    @Autowired
    private AppService appService;

    @GetMapping("/icp_num")
    public ResponseEntity<J> getIcpNumControl() {
        return ResponseEntity.ok(new SuccessR().normal("黑ICP备2025036071号-2"));
    }
    @GetMapping("/copyright")
    public ResponseEntity<J> getCopyrightControl() {
        final String basicYear = "2025";
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy");
        String year = sdf.format(date);
        return ResponseEntity.ok(new SuccessR().normal("B.A.R.C © " +(basicYear.equals(year)? year: basicYear + "-" + year) + " Created by SpringBoot Ver." + appService.getVersion()));
    }
}
