package com.easysign.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easysign.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper
 * <p>
 * 继承 MyBatis-Plus 的 {@link BaseMapper} 获得通用 CRUD。
 * 由另一成员维护，本模块仅做查询引用。
 * </p>
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

}