package com.TheAccountant.controller;

import com.TheAccountant.controller.abstracts.CurrencyHolderController;
import com.TheAccountant.controller.exception.BadRequestException;
import com.TheAccountant.controller.exception.NotFoundException;
import com.TheAccountant.converter.ExpenseConverter;
import com.TheAccountant.converter.NotificationConverter;
import com.TheAccountant.dao.CategoryDao;
import com.TheAccountant.dao.ExpenseDao;
import com.TheAccountant.dto.expense.ExpenseDTO;
import com.TheAccountant.dto.notification.NotificationEntityWrapperDTO;
import com.TheAccountant.model.category.Category;
import com.TheAccountant.model.expense.Expense;
import com.TheAccountant.model.notification.Notification;
import com.TheAccountant.model.user.AppUser;
import com.TheAccountant.service.NotificationService;
import com.TheAccountant.util.CurrencyUtil;
import com.TheAccountant.util.UserUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.transaction.Transactional;
import javax.validation.ConstraintViolationException;
import javax.validation.Valid;
import java.sql.Timestamp;
import java.util.*;

/**
 * REST controller for expense entity
 * 
 * @author Florin
 */
@RestController
@RequestMapping(value = "/expense")
public class ExpenseController extends CurrencyHolderController {

    @Autowired
    private ExpenseDao expenseDao;
    
    @Autowired
    private CategoryDao categoryDao;
    
    @Autowired
    private ExpenseConverter expenseConverter;

    @Autowired
    private NotificationConverter notificationConverter;
    
    @Autowired
    private UserUtil userUtil;

    @Autowired
    private NotificationService notificationService;
    
    @RequestMapping(value = "/add", method = RequestMethod.POST)
    @Transactional
    public ResponseEntity<?> createExpenses(@RequestBody @Valid Expense[] expenses) {
    
        try {
            if (expenses == null || expenses.length == 0) {
                throw new BadRequestException("No expenses found in request!");
            } else {
                List<ExpenseDTO> createdExpenseListDTO = new ArrayList<>();
                int index = 0;
                for (Expense expense : expenses) {
                    if (CurrencyUtil.getCurrency(expense.getCurrency()) == null) {
                        throw new BadRequestException("Wrong currency code for index [" + index + "] and Currency code [" + expense.getCurrency() + "]!");
                    }

                    AppUser user = userUtil.extractLoggedAppUserFromDatabase();
                    Category category = resolveCategory(user, expense.getCategory().getName());
                    if (!user.getDefaultCurrency().getCurrencyCode().equals(expense.getCurrency())) {
                        setDefaultCurrencyAmount(expense, user.getDefaultCurrency());
                    }
                    expense.setCategory(category);
                    expense.setUser(user);

                    ExpenseDTO createdExpenseDto = expenseConverter.convertTo(expenseDao.saveAndFlush(expense));
                    createdExpenseListDTO.add(createdExpenseDto);
                    index++;
                }
                Notification notification = notificationService.registerThresholdNotification(expenses[0].getCategory());
                NotificationEntityWrapperDTO responseDTO = new NotificationEntityWrapperDTO(createdExpenseListDTO,
                        notificationConverter.convertTo(notification));
                return new ResponseEntity<>(responseDTO, HttpStatus.OK);
            }
        } catch (ConstraintViolationException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    //TODO: Why do we have this? If we implement list all expenses it should be based on time interval
    @RequestMapping(value = "/find_all", method = RequestMethod.GET)
    @Transactional
    public ResponseEntity<List<ExpenseDTO>> listAllExpenses() {

        AppUser user = userUtil.extractLoggedAppUserFromDatabase();
        Set<Expense> expenses = expenseDao.findByUsername(user.getUsername());
        if (expenses.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        return new ResponseEntity<>(createExpenseDTOs(expenses), HttpStatus.OK);
    }

    @RequestMapping(value = "/find/{id:.+}/{start_time_millis}/{end_time_millis}", method = RequestMethod.GET)
    public ResponseEntity<List<ExpenseDTO>> listAllExpensesByCategoryAndTimeInterval(
            @PathVariable("id") String id,
            @PathVariable("start_time_millis") long startTimeMillis,
            @PathVariable("end_time_millis") long endTimeMillis) {

        AppUser user = userUtil.extractLoggedAppUserFromDatabase();
        Set<Expense> expenses;
        if (id.equals("*")) {
            expenses = expenseDao.findByTimeInterval(user.getUsername(), new Timestamp(startTimeMillis),
                    new Timestamp(endTimeMillis));
        } else {
            validateIdIsNumber(id);
            expenses = expenseDao.findByTimeIntervalAndCategory(user.getUsername(), Long.valueOf(id),
                    new Timestamp(startTimeMillis), new Timestamp(endTimeMillis));
        }
        if (expenses.isEmpty()) {
            return new ResponseEntity(HttpStatus.NO_CONTENT);
        }
        convertExpensesToDefaultCurrency(expenses, user);
        return new ResponseEntity(createExpenseDTOs(expenses), HttpStatus.OK);
    }


    @RequestMapping(value = "/find/{id}", method = RequestMethod.GET)
    public ResponseEntity<ExpenseDTO> findExpense(@PathVariable("id") Long id) {
    
        AppUser user = userUtil.extractLoggedAppUserFromDatabase();
        Expense expense = expenseDao.findOne(id);
        if (expense == null) {
            throw new NotFoundException("Expense not found");
        }
        if (!(user.getUsername().equals(expense.getUser().getUsername()))) {
            throw new BadRequestException("Unauthorized access");
        }
        return new ResponseEntity<>(expenseConverter.convertTo(expense), HttpStatus.OK);
    }
    
    @RequestMapping(value = "/update/{id}", method = RequestMethod.POST)
    @Transactional
    public ResponseEntity<?> updateExpense(@PathVariable("id") Long id, @RequestBody @Valid Expense expense) {
    
        AppUser user = userUtil.extractLoggedAppUserFromDatabase();
        Expense oldExpense = expenseDao.findOne(id);
        if (oldExpense == null) {
            throw new NotFoundException("Expense not found");
        }
        if (!(user.getUsername().equals(oldExpense.getUser().getUsername()))) {
            throw new BadRequestException("Unauthorized access");
        }
        Category oldCategory = oldExpense.getCategory();
        String newExpenseCategoryName = expense.getCategory().getName();
        if (!oldCategory.getName().equals(newExpenseCategoryName)) {
            Category category = resolveCategory(oldExpense.getUser(), newExpenseCategoryName);
            expense.setCategory(category);
        }
        if(CurrencyUtil.getCurrency(expense.getCurrency()) == null){
            throw new BadRequestException("Wrong currency code");
        }
        expense.setId(id);
        expense.setUser(oldExpense.getUser());
        if(shouldUpdateDefaultCurrencyAmount(expense, user, oldExpense)){
            setDefaultCurrencyAmount(expense,user.getDefaultCurrency());
        }
        expense = expenseDao.saveAndFlush(expense);
        Notification notification = notificationService.registerThresholdNotification(expense.getCategory());
        List<ExpenseDTO> expenseDTOList = new ArrayList<>();
        expenseDTOList.add(expenseConverter.convertTo(expense));
        NotificationEntityWrapperDTO responseDTO = new NotificationEntityWrapperDTO(expenseDTOList,
                notificationConverter.convertTo(notification));

        return new ResponseEntity<>(responseDTO, HttpStatus.OK);
    }

    @RequestMapping(value = "/delete/{id}", method = RequestMethod.DELETE)
    @Transactional
    public ResponseEntity<String> deleteExpense(@PathVariable("id") Long id) {
        try {
            AppUser user = userUtil.extractLoggedAppUserFromDatabase();
            Expense expenseToBeDeleted = expenseDao.findOne(id);
            if (expenseToBeDeleted == null) {
                throw new EmptyResultDataAccessException("Expense not found", 1);
            }
            if (!(user.getUsername().equals(expenseToBeDeleted.getUser().getUsername()))) {
                return new ResponseEntity<>("Unauthorized request", HttpStatus.BAD_REQUEST);
            }
            expenseDao.delete(id);
            expenseDao.flush();
        } catch (EmptyResultDataAccessException emptyResultDataAccessException) {
            throw new NotFoundException("Expense not found");
        }
        return new ResponseEntity<>("Expense deleted", HttpStatus.NO_CONTENT);
    }
    
    @RequestMapping(value = "/delete_all", method = RequestMethod.DELETE)
    @Transactional
    public ResponseEntity<String> deleteAll() {
    
        AppUser user = userUtil.extractLoggedAppUserFromDatabase();
        expenseDao.deleteAllByUsername(user.getUsername());
        expenseDao.flush();
        return new ResponseEntity<>("Expenses deleted", HttpStatus.NO_CONTENT);
    }

    @RequestMapping(value = "/delete_all/{id:.+}", method = RequestMethod.DELETE)
    @Transactional
    public ResponseEntity<String> deleteAllByCategory(@PathVariable("id") long categoryId) {

        AppUser user = userUtil.extractLoggedAppUserFromDatabase();
        expenseDao.deleteAllByCategoryAndUsername(categoryId, user.getUsername());
        expenseDao.flush();
        return new ResponseEntity<>("Expenses deleted", HttpStatus.NO_CONTENT);
    }

    private Category resolveCategory(AppUser user, String categoryName) {
        Category category = categoryDao.findByNameAndUsername(categoryName, user.getUsername());
        if (category == null) {
            category = createAndSaveCategory(categoryName, user);
        }
        return category;
    }

    private Category createAndSaveCategory(String categoryName, AppUser user) {
    
        Category category = new Category();
        category.setName(categoryName);
        category.setUser(user);
        category = categoryDao.saveAndFlush(category);
        return category;
    }
    
    private List<ExpenseDTO> createExpenseDTOs(Set<Expense> expenses) {
    
        List<ExpenseDTO> expenseDTOs = new ArrayList<ExpenseDTO>();
        expenses.stream().forEach(expense -> {
            expenseDTOs.add(expenseConverter.convertTo(expense));
        });
        return expenseDTOs;
    }

    private void convertExpensesToDefaultCurrency(Set<Expense> expenses, AppUser user) {
        expenses.stream().filter(expense -> shouldUpdateDefaultCurrencyAmount(expense, user)).forEach(expense -> {
            setDefaultCurrencyAmount(expense, user.getDefaultCurrency());
            expenseDao.saveAndFlush(expense);
        });
    }

    private void validateIdIsNumber(String id) {
        if(!StringUtils.isNumeric(id)){
            throw new BadRequestException("Id must be either * or a number");
        }
        return;
    }

}
