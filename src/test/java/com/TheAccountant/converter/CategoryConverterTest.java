package com.TheAccountant.converter;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.TheAccountant.dto.category.CategoryDTO;
import com.TheAccountant.model.category.Category;
import com.TheAccountant.model.user.AppUser;

/**
 * Test class for categoryConverter dozer mapping class
 * @author Florin
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/spring-config.xml" })
@TestPropertySource(locations="classpath:application-test.properties")
public class CategoryConverterTest {
    
    @Autowired
    CategoryConverter categoryConverter;

    @Test
    public void shouldConvertCategoryToCategoryDTO() {

        Category category = createCategory();
        CategoryDTO categoryDTO = categoryConverter.convertTo(category);

        assertEquals(category.getId(), categoryDTO.getId());
        assertEquals(category.getName(), categoryDTO.getName());
    }

    @Test
    public void shouldConvertCategoryDTOToCategory() {

        CategoryDTO categoryDTO = createCategoryDTO();
        Category category = categoryConverter.convertFrom(categoryDTO);
        assertEquals(categoryDTO.getId(), category.getId());
        assertEquals(categoryDTO.getName(), category.getName());
    }
    
    private Category createCategory() {
        
        Category category = new Category();
        category.setId(1);
        category.setName("CategTest");
        category.setUser(new AppUser());
        return category;
    }
    
    private CategoryDTO createCategoryDTO() {
        
        CategoryDTO category = new CategoryDTO();
        category.setId(1);
        category.setName("CategDTOTest");
        return category;
    }
}
