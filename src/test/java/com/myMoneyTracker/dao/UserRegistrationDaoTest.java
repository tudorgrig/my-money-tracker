package com.myMoneyTracker.dao;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Date;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import com.myMoneyTracker.controller.AppUserController;
import com.myMoneyTracker.model.user.AppUser;
import com.myMoneyTracker.model.user.UserRegistration;

/**
 * 
 * Test class for user registration dao.
 * 
 * @author Florin
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/spring-config.xml" })
@Transactional
public class UserRegistrationDaoTest {
    
    @Autowired
    private UserRegistrationDao userRegistrationDao;
    
    @Autowired
    private AppUserDao appUserDao;
    
    @Autowired
    private AppUserController appUserController;
    
    @Test
    public void shouldFindAndDeleteByUser() {
    
        AppUser appUser = createAppUser("Florin");
        appUser = appUserDao.save(appUser);
        UserRegistration userRegistration = createUserRegistration("code-test", appUser);
        userRegistrationDao.save(userRegistration);
        List<UserRegistration> regList = userRegistrationDao.findByUserId(appUser.getUserId());
        assertFalse("Could not find userRegistration!", regList.isEmpty());
        userRegistration = regList.get(0);
        userRegistrationDao.delete(userRegistration);
        regList = userRegistrationDao.findByUserId(appUser.getUserId());
        assertTrue("userRegistration should be deleted!", regList.isEmpty());
    }
    
    private UserRegistration createUserRegistration(String code, AppUser user) {
    
        UserRegistration userRegistration = new UserRegistration();
        userRegistration.setCode(code);
        userRegistration.setUser(user);
        return userRegistration;
    }
    
    private AppUser createAppUser(String firstName) {
    
        AppUser appUser = new AppUser();
        appUser.setFirstName(firstName);
        appUser.setSurname("Iacob");
        appUser.setPassword("TEST_PASS");
        appUser.setUsername("florin");
        appUser.setBirthdate(new Date());
        appUser.setEmail("rampageflo2@gmail.com");
        return appUser;
    }
}
