package com.mvgv70.xposed_mtc_bt;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class Main implements IXposedHookLoadPackage {
	
  private final static int NUMBER_LEN = 10;
  private final static String TAG = "xposed-mtc-bt";
  private static Activity btActivity;
  private static boolean phoneBookSorted = false;
  private static Object phonebookFragment;
  private static keyboardDialog kbDialog = null;
  private static List<Character> enabledChars = new ArrayList<Character>();
		
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
    XC_MethodReplacement updatePhoneBookFirstChar = new XC_MethodReplacement() {
        
      @Override
      protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"updatePhoneBookFirstChar, phoneBookSorted="+phoneBookSorted);
        btActivity = (Activity)param.thisObject;
        if (!phoneBookSorted)
          XposedHelpers.callMethod(param.thisObject, "assortPhoneBook");
        @SuppressWarnings("unchecked")
        List<String> phoneBookList = (List<String>)XposedHelpers.getObjectField(param.thisObject, "phoneBookList");
        List<Character> phoneBookFirstChar = new ArrayList<Character>();
        // перебираем контакты в телефонной книге
        Character ch;
        enabledChars.clear();
        for (String line : phoneBookList)
        {
          // первый символ имени абонента
          ch = Character.toUpperCase(line.charAt(0));
          // добавляем символ в список
          phoneBookFirstChar.add(ch);
          if (!enabledChars.contains(ch)) enabledChars.add(ch);
        }
        // устанавливаем список с первыми буквами
        XposedHelpers.setObjectField(param.thisObject, "phoneBookFirstChar", phoneBookFirstChar);
        Log.d(TAG,"updatePhoneBookFirstChar OK");
        return null;
      }
    };
    
    // BlueToothActivity.onDestroy()
    XC_MethodHook onDestroy = new XC_MethodHook() {
        
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"onDestroy");
        phoneBookSorted = false;
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
        phoneBookSorted = true;
        Log.d(TAG,"sorted="+phoneBookSorted);
        return null;
      }
    };
    
    // Ui1.postKeyboadrShow()
    XC_MethodReplacement postKeyboadrShow = new XC_MethodReplacement() {
        
      @Override
      protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"postKeyboadrShow");
        Activity mActivity = (Activity)XposedHelpers.getObjectField(param.thisObject, "mActivity");
        phonebookFragment = XposedHelpers.getObjectField(param.thisObject, "phonebookFragment");
        TableLayout view = new TableLayout(mActivity);
        // фоновая картинка
        Resources res = btActivity.getResources();
        int button_bkgnd_id = res.getIdentifier("keyboardbtn", "drawable", btActivity.getPackageName());
        // строки
        for (int row=0; row < 6; row++)
        {
          TableRow tableRow = new TableRow(mActivity);
          tableRow.setGravity(Gravity.CENTER);
          tableRow.setLayoutParams(new TableLayout.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.MATCH_PARENT, 1.0f));
          // столбцы
          for (int col=0; col < 5; col++)
          {
            TextView button = new TextView(mActivity);
            int index = col+row*5;
            char ch = decodeChar(index);
            button.setText(String.valueOf(ch));
            button.setGravity(Gravity.CENTER);
            button.setBackgroundResource(button_bkgnd_id);
            button.setEnabled(ch != ' ');
            button.setClickable(ch != ' ');
            button.setOnClickListener(searchClick);
            TableRow.LayoutParams buttonParams = new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.MATCH_PARENT);
            buttonParams.setMargins(10, 10, 10, 10);
            button.setLayoutParams(buttonParams);
            // добавляем кнопку в строку
            tableRow.addView(button);
          }
          // добавляем строку кнопок
          view.addView(tableRow);
        }
        // диалог
        kbDialog = new keyboardDialog(mActivity);
        kbDialog.setView(view, 0, 0, 0, 0);
        kbDialog.setCancelable(true);
        kbDialog.show();
        return null;
      }
    };
    
    // com.microntek.bluetooth.ui.PhonebookFragment$PhoneBookAdapter.getView(int,View,ViewGroup)
    XC_MethodReplacement getView = new XC_MethodReplacement() {
        
      @Override
      protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
    	int position = (int)param.args[0];
    	View view = (View)param.args[1];
    	Resources res = btActivity.getResources();
        // inflate
    	if (view == null)
    	{
    	  int layout_id = res.getIdentifier("pblist", "layout", btActivity.getPackageName());
    	  Object mInflater = XposedHelpers.getObjectField(param.thisObject, "mInflater");
    	  view = (View)XposedHelpers.callMethod(mInflater, "inflate", layout_id, null);
    	}
    	// текущая строка
    	String curRow = (String)XposedHelpers.callMethod(param.thisObject, "getItem", position);
    	Character curChar = ' ';
        String curName = "";
        String curNumber = "";
        String[] curValues = curRow.split(Pattern.quote("^"));
        if (curValues.length >= 2)
        {
          curName = curValues[0];
          curNumber = curValues[1];
          if (!curName.isEmpty()) curChar = Character.toUpperCase(curName.charAt(0)); 
        }
        Log.d(TAG,curChar+": curName="+curName+", curNumber="+curNumber);
    	// предыдущая строка
    	Character prevChar = ' ';
    	String prevName = "";
    	if (position > 0)
    	{
    	  String prevRow = (String)XposedHelpers.callMethod(param.thisObject, "getItem", position-1);
    	  String[] prevValues = prevRow.split(Pattern.quote("^"));
          if (prevValues.length >= 2)
          {
            prevName = prevValues[0];
            if (!prevName.isEmpty()) prevChar = Character.toUpperCase(prevName.charAt(0));
          }
          Log.d(TAG,prevChar+": prevName="+prevName);
    	}
        // отображаемые элементы
        int char_id = res.getIdentifier("tv_firstchar", "id", btActivity.getPackageName());
        int name_id = res.getIdentifier("tv_friendname", "id", btActivity.getPackageName());
        int number_id = res.getIdentifier("tv_friendnumber", "id", btActivity.getPackageName());
        int sep_id = res.getIdentifier("iv_sep", "id", btActivity.getPackageName());
        int pbbg_id = res.getIdentifier("iv_pbbg", "id", btActivity.getPackageName());
        // view
        TextView charView = (TextView)view.findViewById(char_id);
        TextView nameView = (TextView)view.findViewById(name_id);
        TextView numberView = (TextView)view.findViewById(number_id);
        View sepView = view.findViewById(sep_id);
        View pbbgView = view.findViewById(pbbg_id);
        // устанавливаем значения
        charView.setText(curChar.toString());
        nameView.setText(curName);
        numberView.setText(curNumber);
        // изменился первый символ
        if (curChar.equals(prevChar))
          charView.setVisibility(View.INVISIBLE);
        else
          charView.setVisibility(View.VISIBLE);
        // изменилось имя абонента
        if (curName.equals(prevName))
          sepView.setVisibility(View.INVISIBLE);
        else
          sepView.setVisibility(View.VISIBLE);
        // выделение строки
        int selected = XposedHelpers.getIntField(param.thisObject, "seleteItem");
        int resource_id;
        if (position != selected)
          resource_id = res.getIdentifier("list_selector", "drawable", btActivity.getPackageName());
        else
          resource_id = res.getIdentifier("history_listbg", "drawable", btActivity.getPackageName());
        pbbgView.setBackgroundResource(resource_id);
    	// return
    	return view;
      }
    };
    
    // begin hooks
    if (!lpparam.packageName.equals("com.microntek.bluetooth")) return;
    XposedHelpers.findAndHookMethod("com.microntek.bluetooth.notification.btNotificationReceiver", lpparam.classLoader, "getNumName", List.class, String.class, getNumName);
    XposedHelpers.findAndHookMethod("com.microntek.bluetooth.BlueToothActivity", lpparam.classLoader, "getNameOfNumbers", String.class, getNameOfNumbers);
    XposedHelpers.findAndHookMethod("com.microntek.bluetooth.BlueToothActivity", lpparam.classLoader, "assortPhoneBook", assortPhoneBook);
    XposedHelpers.findAndHookMethod("com.microntek.bluetooth.BlueToothActivity", lpparam.classLoader, "updatePhoneBookFirstChar", updatePhoneBookFirstChar);
    XposedHelpers.findAndHookMethod("com.microntek.bluetooth.BlueToothActivity", lpparam.classLoader, "onDestroy", onDestroy);
    XposedHelpers.findAndHookMethod("com.microntek.bluetooth.ui.PhonebookFragment$PhoneBookAdapter", lpparam.classLoader, "getView", int.class, View.class, ViewGroup.class, getView);
    // ищем используемый Ui
    for (int i=1; i<=5; i++)
    {
      try
      {
        XposedHelpers.findAndHookMethod("com.microntek.bluetooth.ui.Ui"+i, lpparam.classLoader, "postKeyboadrShow", postKeyboadrShow);
        Log.d(TAG,"Ui"+i+" detected...");
        break;
      }
      catch (Error e) {}
    }
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
  
  public View.OnClickListener searchClick = new View.OnClickListener() 
  {
    public void onClick(View v) 
    {
      Log.d(TAG,"search button press");
      try
      {
        TextView view = (TextView)v;
        Log.d(TAG,""+view.getText());
        XposedHelpers.callMethod(phonebookFragment, "search", view.getText().charAt(0));
        Log.d(TAG,"search OK");
        if (kbDialog != null) kbDialog.dismiss();
        Log.d(TAG,"dismiss OK");
      }
      catch (Exception e)
      {
        Log.e(TAG,"search exception: "+e.getMessage());
        if (kbDialog != null) kbDialog.dismiss();
      }
    }
  };

  private static char decodeChar(int index)
  {
    if (index < enabledChars.size())
      return enabledChars.get(index);
    else
      return ' ';
  }
    
  private class keyboardDialog extends AlertDialog
  {
    public keyboardDialog(Context context)
    {
      super(context);
    }
    
    @Override
    public void onStop()
    {
      kbDialog = null;
    }
  }

}