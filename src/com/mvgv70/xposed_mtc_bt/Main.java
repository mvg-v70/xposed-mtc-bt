package com.mvgv70.xposed_mtc_bt;

import java.util.List;

import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
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
	    
    // begin hooks
    if (!lpparam.packageName.equals("com.microntek.bluetooth")) return;
    XposedHelpers.findAndHookMethod("com.microntek.bluetooth.notification.btNotificationReceiver", lpparam.classLoader, "getNumName", List.class, String.class, getNumName);
    XposedHelpers.findAndHookMethod("com.microntek.bluetooth.BlueToothActivity", lpparam.classLoader, "getNameOfNumbers", String.class, getNameOfNumbers);
    Log.d(TAG,"com.microntek.bluetooth OK");
  }
  
  // ����� ������ � ������ ���������
  private String findNumber(List<String> phoneBook, String number)
  {
    int len;
    Log.d(TAG,"number -> "+number+", phonebook.count="+phoneBook.size());
    String result = number;
    String contact_number;
    // ��������� NUMBER_LEN �������� ��������� ������
    len = number.length();
    if (len > NUMBER_LEN)
      number = number.substring(len-NUMBER_LEN);
    // ���� �� ������� � ���������� �����
    for (String contact : phoneBook) 
    {
      int k = contact.indexOf('^');
      // �������� � ����������, ���� �� ������ �����������
      if (k == -1) continue;
      int i = contact.indexOf('^',k+1);
      // ���� �� ������ ������ �����������
      if (i == -1) i = contact.length();
      contact_number = contact.substring(k+1,i);
      // ��������� ��� ������� ����� ����
      contact_number = contact_number.replaceAll("[^0123456789]","");
      // ������� ��������� NUMBER_LEN ����
      len = contact_number.length();
      if (len > NUMBER_LEN)
        contact_number = contact_number.substring(len-NUMBER_LEN);
      // ���������
      if (number.equals(contact_number))
      {
    	result = contact.substring(0,k);
        break;
      }
    }
    Log.d(TAG,"contact_name -> "+result);
    // ���������
    return result;
  }

}