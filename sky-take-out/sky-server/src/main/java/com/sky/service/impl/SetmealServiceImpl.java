package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SetmealServiceImpl implements SetmealService {
    @Autowired
    private SetmealMapper setmealMapper;

    @Autowired
    private SetmealDishMapper setmealDishMapper;

    /**
     * 添加套餐和关联菜品
     *
     * @param setmealDTO
     */
    public void saveWithDish(SetmealDTO setmealDTO){
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        setmealMapper.insert(setmeal);

        Long setmealId = setmeal.getId();
        //关联菜品
        List<SetmealDish> Dishes = setmealDTO.getSetmealDishes();
        for (SetmealDish setmealDish : Dishes) {
            setmealDish.setSetmealId(setmealId);
            setmealDishMapper.insert(setmealDish);
        }

    }

    /**
     * 分页查询套餐
     *
     * @param setmealPageQueryDTO
     * @return
     */
    public PageResult pagequery(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageHelper.startPage(setmealPageQueryDTO.getPage(), setmealPageQueryDTO.getPageSize());
        Page<SetmealVO> page = setmealMapper.pagequery(setmealPageQueryDTO);
        Long total = page.getTotal();
        List<SetmealVO> setmealVOList = page.getResult();
        return new PageResult(total, setmealVOList);
    }

    /**
     * 批量删除套餐
     *
     * @param ids
     */
    public void deleteBatch(List<Long> ids){
        //判断套餐是否能被删除————启售状态不能被删除
        for (Long id : ids) {
            Setmeal setmeal = setmealMapper.getById(id);
            if (setmeal.getStatus() == StatusConstant.ENABLE) {
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
            }
        }
        //删除套餐
        for (Long id : ids) {
            setmealMapper.deleteById(id);
            //删除套餐和菜品的关联关系
            setmealDishMapper.deleteBySetmealId(id);
        }

    }

    /**
     * 根据id查询套餐
     *
     * @param id
     * @return
     */
    public SetmealVO findById(Long id){
        Setmeal setmeal = setmealMapper.getById(id);
        SetmealVO setmealVO = new SetmealVO();
        BeanUtils.copyProperties(setmeal, setmealVO);
        List<SetmealDish> setmealDishes = setmealDishMapper.getDishIdsBySetmealId(id);
        setmealVO.setSetmealDishes(setmealDishes);
        return setmealVO;
    }
}
