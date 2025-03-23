package com.sky.service;

import com.sky.dto.SetmealDTO;

public interface SetmealService {
    /**
     * 添加套餐和关联菜品
     *
     * @param setmealDTO
     */
    void saveWithDish(SetmealDTO setmealDTO);
}
