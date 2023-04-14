package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private IFollowService followService;

    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUerId, @PathVariable("isFollow") Boolean isFollow) {
        return followService.follow(followUerId, isFollow);
    }

    @GetMapping("/or/not/{id}")
    public Result followOrNot(@PathVariable("id") Long followUerId) {
        return followService.followOrNot(followUerId);
    }

}
