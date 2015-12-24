package com.mvgv70.xposed_mtc_bt;

import java.util.ArrayList;
import java.util.List;

import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class Main implements IXposedHookLoadPackage {
	
  private final static int NUMBER_LEN = 10;
  private final static String TAG = "xposed-mtc-bt";  
		
  @Override
  public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
    
    // String btNotificationReceiver.getNumName(List<String>,String)
    XC_MethodReplacement getNumName = new XC_MethodReplacement() {
      
      @Override
      protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
       Log.d(TAG,"getNumName");
       @SuppressWarnings("unchecked")
       List<String> phoneBook = (List<String>)param.args[0];
       String phoneNumber = (String)param.args[1];
       String contact_name = findNumber(phoneBook,phoneNumber);
       return contact_name;
      }
    };
    
    // BlueToothActivity.getNameOfNumbers(String)
    XC_MethodReplacement getNameOfNumbers = new XC_MethodReplacement() {
        
      @Override
      protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"getNameOfNumbers");
        @SuppressWarnings("unchecked")
		List<String> phoneBook = (List<String>)XposedHelpers.getObjectField(param.thisObject, "phoneBookList");
        String phoneNumber = (String)param.args[0];
        String contact_name = findNumber(phoneBook,phoneNumber);
        return contact_name;
      }
    };
    
    // BlueToothActivity.updatePhoneBookFirstChar()
    XC_MethodHook updatePhoneBookFirstChar = new XC_MethodHook() {
        
      @Override
      protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"updatePhoneBookFirstChar");
        XposedHelpers.callMethod(param.thisObject, "assortPhoneBook");
      }
    };
    
    // PreferenceProc.assortPhoneBook()
    XC_MethodReplacement assortPhoneBook = new XC_MethodReplacement() {
        
      @Override
      protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"assortPhoneBook");
        @SuppressWarnings("unchecked")
		List<String> phoneBookList = (List<String>)XposedHelpers.getObjectField(param.thisObject, "phoneBookList");
        Log.d(TAG,"phoneBookList.size="+phoneBookList.size());
        if (phoneBookList.size() == 0) return null;
        // отсортированный список
        List<String> phoneBookListSorted = new ArrayList<String>();
        // русские буквы
        for (Character ch = 'А'; ch <= 'Я'; ch++)
        {
          for (String line : phoneBookList)
            if (Character.toUpperCase(line.charAt(0)) == ch)
              phoneBookListSorted.add(line);
        }
        // английские буквы
        for (Character ch = 'A'; ch <= 'Z'; ch++)
        {
          for (String line : phoneBookList)
            if (Character.toUpperCase(line.charAt(0)) == ch)
              phoneBookListSorted.add(line);
        }
        // символы и цифры
        for (Character ch = '!'; ch <= '9'; ch++)
        {
          for (String line : phoneBookList)
            if (Character.toUpperCase(line.charAt(0)) == ch)
              phoneBookListSorted.add(line);
        if (phoneBookListSorted.size() < phoneBookList.size())
        {
          // добавим записи, которые не были добавлены
          for (String line : phoneBookList)
            if (!phoneBookListSorted.contains(line))
              phoneBookListSorted.add(line);
          }
        }
        // устанавливаем отсортированный список
        XposedHelpers.setObjectField(param.thisObject, "phoneBookList", phoneBookListSorted);
        return null;
      }
    };
    
    // begin hooks
    if (!lpparam.packageName.equals("com.microntek.bluetooth")) return;
    XposedHelpers.findAndHookMethod("com.microntek.bluetooth.notification.btNotificationReceiver", lpparam.classLoader, "getNumName", List.class, String.class, getNumName);
    XposedHelpers.findAndHookMethod("com.microntek.bluetooth.BlueToothActivity", lpparam.classLoader, "getNameOfNumbers", String.class, getNameOfNumbers);
    XposedHelpers.findAndHookMethod("com.microntek.bluetooth.BlueToothActivity", lpparam.classLoader, "assortPhoneBook", assortPhoneBook);
    XposedHelpers.findAndHookMethod("com.microntek.bluetooth.BlueToothActivity", lpparam.classLoader, "updatePhoneBookFirstChar", updatePhoneBookFirstChar);
    Log.d(TAG,"com.microntek.bluetooth OK");
  }
  
  // поиск номера в списке контактов
  private String findNumber(List<String> phoneBook, String number)
  {
    int len;
    Log.d(TAG,"number -> "+number+", phonebook.count="+phoneBook.size());
    String result = number;
    String contact_number;
    // последние NUMBER_LEN символов входящего номера
    len = number.length();
    if (len > NUMBER_LEN)
      number = number.substring(len-NUMBER_LEN);
    // цикл по записям в телефонной книге
    for (String contact : phoneBook) 
    {
      int k = contact.indexOf('^');
      // перейдем к следующему, если не найден разделитель
      if (k == -1) continue;
      int i = contact.indexOf('^',k+1);
      // если не найден второй разделитель
      if (i == -1) i = contact.length();
      contact_number = contact.substring(k+1,i);
      // выбросить все символы кроме цифр
      contact_number = contact_number.replaceAll("[^0123456789]","");
      // возьмем последние NUMBER_LEN цифр
      len = contact_number.length();
      if (len > NUMBER_LEN)
        contact_number = contact_number.substring(len-NUMBER_LEN);
      // сравнение
      if (number.equals(contact_number))
      {
    	result = contact.substring(0,k);
        break;
      }
    }
    Log.d(TAG,"contact_name -> "+result);
    // результат
    return result;
  }

}