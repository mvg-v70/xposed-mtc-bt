package com.mvgv70.xposed_mtc_bt;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.mvgv70.utils.IniFile;
import com.mvgv70.utils.Utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnLongClickListener;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class Main implements IXposedHookLoadPackage {
	
  private static int NUMBER_LEN = 10;
  private static Activity btActivity;
  private static Context context;
  // KGL == 1, KLD,JY == 0
  private static int mUiType = 0;
  private static boolean phoneBookSorted = false;
  private static boolean afterSync = false;
  private static Object phonebookFragment;
  private static Object dialFragment;
  private static keyboardDialog kbDialog = null;
  private static List<Character> enabledChars = new ArrayList<Character>();
  private static IniFile props = new IniFile();
  private static String EXTERNAL_SD = "/mnt/external_sd/";
  private static String INI_FILE_NAME = EXTERNAL_SD+"mtc-bt/mtc-bt.ini";
  private static final String QUICKDIAL_SECTION = "quickdial";
  private static final String SETTINGS_SECTION = "settings";
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
        List<String> phoneBook = (List<String>)Utils.getObjectField(param.thisObject, "phoneBookList");
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
        if (!phoneBookSorted)
          Utils.callMethod(param.thisObject, "assortPhoneBook");
        @SuppressWarnings("unchecked")
        List<String> phoneBookList = (List<String>)Utils.getObjectField(param.thisObject, "phoneBookList");
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
        Utils.setObjectField(param.thisObject, "phoneBookFirstChar", phoneBookFirstChar);
        Log.d(TAG,"updatePhoneBookFirstChar OK");
        return null;
      }
    };
    
    // BlueToothActivity.onCreate(Bundle)
    XC_MethodHook onCreate = new XC_MethodHook() {
        
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"onCreate");
        btActivity = (Activity)param.thisObject;
        afterSync = false;
        // показать версию модуля
        try 
        {
          context = btActivity.createPackageContext(getClass().getPackage().getName(), Context.CONTEXT_IGNORE_SECURITY);
          String version = context.getString(R.string.app_version_name);
          Log.d(TAG,"version="+version);
          Log.d(TAG,"android "+Build.VERSION.RELEASE);
        } catch (NameNotFoundException e) {}
        Object mUi = Utils.getObjectField(param.thisObject, "mUi");
        mUiType = (int)Utils.callMethod(mUi, "getUiType");
        Log.d(TAG,"UiType="+mUiType);
        // путь к файлу из build.prop
        EXTERNAL_SD = Utils.getModuleSdCard();
        readSettings();
        // ACTION_CALL
        if (btActivity.getIntent().getAction().equals(Intent.ACTION_CALL))
        {
          callNumber(btActivity.getIntent(), param.thisObject);
        }
      }
    };
    
    // BlueToothActivity.onNewIntent(Intent)
    XC_MethodHook onNewIntent = new XC_MethodHook() {
        
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"onNewIntent");
        if (btActivity.hashCode() == param.thisObject.hashCode())
        {
          Intent intent = (Intent)param.args[0];
          if (intent.getAction().equals(Intent.ACTION_CALL))
          {
            // ACTION_CALL
            callNumber(intent,param.thisObject);
          }
        }
      }
    };
    
    // BlueToothActivity.onDestroy()
    XC_MethodHook onDestroy = new XC_MethodHook() {
        
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"onDestroy");
        phoneBookSorted = false;
        afterSync = false;
      }
    };
    
    // BlueToothActivity.assortPhoneBook()
    XC_MethodReplacement assortPhoneBook = new XC_MethodReplacement() {
        
      @SuppressWarnings("unchecked")
      protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"assortPhoneBook");
        List<String> phoneBookList;
        if (afterSync)
        {
          // возьмем список из mBtDevice после синхронизации контактов
          Object mBtDevice = Utils.getObjectField(param.thisObject, "mBtDevice");
          phoneBookList = (List<String>)Utils.getObjectField(mBtDevice, "reportPhonebookList");
        }
        else
          phoneBookList = (List<String>)Utils.getObjectField(btActivity, "phoneBookList");
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
        Utils.setObjectField(param.thisObject, "phoneBookList", phoneBookListSorted);
        phoneBookSorted = true;
        afterSync = false;
        Log.d(TAG,"sorted="+phoneBookSorted);
        return null;
      }
    };
    
    // Ui1.postKeyboadrShow()
    XC_MethodReplacement postKeyboadrShow = new XC_MethodReplacement() {
        
      @Override
      protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"postKeyboadrShow");
        Activity mActivity = btActivity;
        phonebookFragment = Utils.getObjectField(param.thisObject, "phonebookFragment");
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
    	    LayoutInflater mInflater = (LayoutInflater)Utils.getObjectField(param.thisObject, "mInflater");
    	    view = mInflater.inflate(layout_id, null);
    	  }
    	  // текущая строка
    	  String curRow = (String)Utils.callMethod(param.thisObject, "getItem", position);
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
    	  // предыдущая строка
    	  Character prevChar = ' ';
    	  String prevName = "";
    	  if (position > 0)
    	  {
    	    String prevRow = (String)Utils.callMethod(param.thisObject, "getItem", position-1);
    	    String[] prevValues = prevRow.split(Pattern.quote("^"));
          if (prevValues.length >= 2)
          {
            prevName = prevValues[0];
            if (!prevName.isEmpty()) prevChar = Character.toUpperCase(prevName.charAt(0));
          }
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
        int selected = Utils.getIntField(param.thisObject, "seleteItem");
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
    
    // DialFragment.init()
    XC_MethodHook init = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"init");
        int button_id;
        View button;
        String number;
        dialFragment = param.thisObject;
        Resources res = btActivity.getResources();
        // покажем mac-адрес телефона
        long lmac_address = (long)Utils.callMethod(btActivity, "getConnectPhoneMACaddr");
        String mac_address = Long.toHexString(lmac_address);
        Log.d(TAG,"mac_address="+mac_address);
        Log.d(TAG,"mUiType="+mUiType);
        // на какие кнопки нужно повесить long-click
        for (int i=1; i<=9; i++)
        {
          number = getQuickDial(i);
          if (mUiType == 1)
            // KGL
            button_id = res.getIdentifier("bt_num"+i, "id", btActivity.getPackageName());
          else if (mUiType == 0)
            // KLD & others
            button_id = res.getIdentifier("dial_num"+i, "id", btActivity.getPackageName());
          else
            button_id = 0;
          button = btActivity.findViewById(button_id);
          if ((button != null) && (!number.isEmpty()))
          {
            Log.d(TAG,"set long click for "+i);
            button.setLongClickable(true);
            button.setOnLongClickListener(buttonLongClick);
            button.setTag((Object)i);
          }
        }
        Log.d(TAG,"init OK");
      }
    };
    
    // BTDevice.syncPhonebook()
    XC_MethodHook syncPhonebook = new XC_MethodHook() {
        
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"syncPhonebook");
      }
    };
    
    // BTDevice.syncPhonebookEnd()
    XC_MethodHook syncPhonebookEnd = new XC_MethodHook() {
        
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"syncPhonebookEnd");
        afterSync = true;
      }
    };

    // begin hooks
    if (!lpparam.packageName.equals("com.microntek.bluetooth")) return;
    Utils.readXposedMap();
    Utils.setTag(TAG);
    Utils.findAndHookMethod("com.microntek.bluetooth.notification.btNotificationReceiver", lpparam.classLoader, "getNumName", List.class, String.class, getNumName);
    Utils.findAndHookMethod("com.microntek.bluetooth.BlueToothActivity", lpparam.classLoader, "onCreate", Bundle.class, onCreate);
    Utils.findAndHookMethod("android.app.Activity", lpparam.classLoader, "onNewIntent", Intent.class, onNewIntent);
    Utils.findAndHookMethod("com.microntek.bluetooth.BlueToothActivity", lpparam.classLoader, "getNameOfNumbers", String.class, getNameOfNumbers);
    Utils.findAndHookMethod("com.microntek.bluetooth.BlueToothActivity", lpparam.classLoader, "assortPhoneBook", assortPhoneBook);
    Utils.findAndHookMethod("com.microntek.bluetooth.BlueToothActivity", lpparam.classLoader, "updatePhoneBookFirstChar", updatePhoneBookFirstChar);
    Utils.findAndHookMethod("com.microntek.bluetooth.BlueToothActivity", lpparam.classLoader, "onDestroy", onDestroy);
    Utils.findAndHookMethod("com.microntek.bluetooth.ui.PhonebookFragment$PhoneBookAdapter", lpparam.classLoader, "getView", int.class, View.class, ViewGroup.class, getView);
    Utils.findAndHookMethod("com.microntek.bluetooth.ui.DialFragment", lpparam.classLoader, "init", init);
    Utils.findAndHookMethod("com.microntek.bluetooth.BTDevice", lpparam.classLoader, "syncPhonebook", syncPhonebook);
    Utils.findAndHookMethod("com.microntek.bluetooth.BTDevice", lpparam.classLoader, "syncPhonebookEnd", syncPhonebookEnd);
    //
    // ищем используемый Ui
    for (int i=1; i<=5; i++)
    {
      try
      {
        Utils.findAndHookMethod("com.microntek.bluetooth.ui.Ui"+i, lpparam.classLoader, "postKeyboadrShow", postKeyboadrShow);
        Log.d(TAG,"Ui"+i+" detected...");
        break;
      }
      catch (Error e) {}
    }
    Log.d(TAG,"com.microntek.bluetooth OK");
  }
  
  // чтение настроек
  private void readSettings()
  {
    try
    {
      INI_FILE_NAME = EXTERNAL_SD+"mtc-bt/mtc-bt.ini";
      Log.d(TAG,"inifile load from "+INI_FILE_NAME);
      props.clear(); 
      props.loadFromFile(INI_FILE_NAME);
      NUMBER_LEN = props.getIntValue(SETTINGS_SECTION, "importantNumbers", 10);
      Log.d(TAG,"importantNumbers="+NUMBER_LEN);
    } 
    catch (Exception e) 
    {
      Log.e(TAG,e.getMessage());
    }
  }
  
  private String getQuickDial(int index)
  {
    String number = ""; 
    String mac_address = null;
    long lmac_address = (long)Utils.callMethod(btActivity, "getConnectPhoneMACaddr");
    if (lmac_address > 0)
    {
      mac_address = Long.toHexString(lmac_address);
      // локальный набор для подключенного телефона
      number = props.getValue(QUICKDIAL_SECTION+"."+mac_address, Integer.toString(index), "");
    }
    if (number.isEmpty())
    {
      // глобальный список
      number = props.getValue(QUICKDIAL_SECTION, Integer.toString(index), "");
    }
    return number;
  }

  private void callNumber(Intent intent, Object thisObject)
  {
    // телефонный номер
    String tel = intent.getData().toString().substring(4);
    Log.d(TAG,"tel="+tel);
    try
    {
      tel = URLDecoder.decode(tel,"UTF-8");
    }
    catch (Exception e) {}
    Log.d(TAG,"decoded tel="+tel);
    if (!TextUtils.isEmpty(tel))
    {
      // выбросить все символы кроме цифр и +
      tel = tel.replaceAll("[^+0123456789]","");
      Log.d(TAG,"ACTION_CALL - tel:"+tel);
      // переключаемся на старницы набора номера 
      Object mUi = Utils.getObjectField(thisObject, "mUi");
      Utils.callMethod(mUi, "SwitchToPage", 1);
      Handler handler = (Handler)Utils.getObjectField(thisObject, "uiHandler");
      // позвонить по номеру
      callNumber.setNumber(tel);
      handler.post(callNumber); // или postDelayed()
    }
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
        Utils.callMethod(phonebookFragment, "search", view.getText().charAt(0));
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
  
  private OnLongClickListener buttonLongClick = new OnLongClickListener()
  {
    public boolean onLongClick(View v)
    {
      int index = (int)v.getTag();
      Log.d(TAG,"long click "+index);
      String number = getQuickDial(index);
      // проверка пустого номера
      if (number.isEmpty()) return false;
      StringBuffer dialoutNumbers = (StringBuffer)Utils.getObjectField(dialFragment, "dialoutNumbers");
      // если ничего не введено
      if (dialoutNumbers.length() > 0) return false;
      // добавляем номер телефона в набор
      dialoutNumbers.append(number);
      dialNumber(dialoutNumbers);
      return true;
    }
  };
  
  // набор номер
  private void dialNumber(StringBuffer dialoutNumbers)
  {
    Utils.setObjectField(dialFragment, "dialoutNumbers", dialoutNumbers);
    // звонок
    if (mUiType == 1)
      // KGL
      Utils.callMethod(dialFragment, "dial");
    else
      // KLD & others
      Utils.callMethod(dialFragment, "dialAnswer");
  }
  
  // звонок через Runnable
  private class CallRunnable implements Runnable
  {
	private StringBuffer number = null; 
	
	public void setNumber(String text)
	{
	  number = new StringBuffer(text);
	}
	
    public void run() 
    {
      Log.d(TAG,"dial "+number);
      dialNumber(number);
    }
  };
  
  private CallRunnable callNumber = new CallRunnable();

  
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