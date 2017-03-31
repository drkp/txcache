/*
 * RUBiS
 * Copyright (C) 2002, 2003, 2004 French National Institute For Research In Computer
 * Science And Control (INRIA).
 * Contact: jmob@objectweb.org
 * 
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation; either version 2.1 of the License, or any later
 * version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
 *
 * Initial developer(s): Emmanuel Cecchet, Julie Marguerite
 * Contributor(s): 
 */
 package edu.rice.rubis.client;

import java.util.GregorianCalendar;
import java.lang.String;

/** This class provides additionnal functions that the GregorianCalendar
 * class does not provide. It is mainly to compute time differences and
 * display the date in a database understandable format.
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */

public class TimeManagement extends GregorianCalendar
{

  /**
   * Creates a new <code>TimeManagement</code> instance.
   *
   */
  public TimeManagement()
  {
  }


  /** Returns a string representation of the current date (when the method is called)
   * conforming to the following database format : 'YYYY-MM-DD hh:mm:ss'
   *
   * @return current date in database format
   *
   */
  public static String currentDateToString()
  {
    GregorianCalendar d = new GregorianCalendar();
    return dateToString(d);
  }


  /**
   * Returns a string representation of a date conforming to 
   * the following database format : 'YYYY-MM-DD hh:mm:ss'
   *
   * @param d a <code>GregorianCalendar</code> value
   * @return current date in database format
   *
   */
  public static String dateToString(GregorianCalendar d)
  {
    String result;
    int    year, month, day, hour, minute, second;

    year   = d.get(d.YEAR);
    month  = d.get(d.MONTH) + 1;
    day    = d.get(d.DATE);
    hour   = d.get(d.HOUR_OF_DAY);
    minute = d.get(d.MINUTE);
    second = d.get(d.SECOND);
    result = year+"-"+month+"-"+day+" "+hour+":"+minute+":"+second;
    return result;
  }


  /** Returns the time elapsed between startDate and endDate in milliseconds
   *
   * @param startDate beginning date
   * @param endDate ending date
   *
   * @return elapsed time in milliseconds
   *
   */
  public static long diffTimeInMs(GregorianCalendar startDate, GregorianCalendar endDate)
  {
    long  year, month, day, hour, minute, second, millis;

    year   = endDate.get(endDate.YEAR)-startDate.get(startDate.YEAR);
    month  = endDate.get(endDate.MONTH)-startDate.get(startDate.MONTH);
    day    = endDate.get(endDate.DATE)-startDate.get(startDate.DATE);
    hour   = endDate.get(endDate.HOUR_OF_DAY)-startDate.get(startDate.HOUR_OF_DAY);
    minute = endDate.get(endDate.MINUTE)-startDate.get(startDate.MINUTE);
    second = endDate.get(endDate.SECOND)-startDate.get(startDate.SECOND);
    millis = endDate.get(endDate.MILLISECOND)-startDate.get(startDate.MILLISECOND);
 
    if (millis < 0)
    {
      millis = millis + 1000;
      second = second - 1;
    }
    if (second < 0)
    {
      second = second + 60;
      minute = minute - 1;
    }
    if (minute < 0)
    {
      minute = minute + 60;
      hour = hour - 1;
    }
    if (hour < 0)
    {
      hour = hour + 24;
      day = day - 1;
    }
    if (day < 0)
    {
      day = day + startDate.getActualMaximum(startDate.DAY_OF_MONTH); // is the same as startDate.DATE
      month = month - 1;
    }
    if (month < 0)
    {
      month = month + 12;
      year = year - 1;
    }
    return millis+(second*1000)+(minute*60000)+(hour*3600000)+(day*24*3600000)+(month*30*24*3600000); // We approximate 30 days per month
  }


  /** Returns a string representation of the time elapsed between startDate
   * and endDate. Example of a returned string : "1 month 3 days 6 hours 33 minutes 4 seconds 234 milliseconds"
   *
   * @param startDate beginning date
   * @param endDate ending date
   *
   * @return string containing the time difference up to the millisecond
   *
   */
  public static String diffTime(GregorianCalendar startDate, GregorianCalendar endDate)
  {
    int    year, month, day, hour, minute, second, millis;
    String result = "";

    year   = endDate.get(endDate.YEAR)-startDate.get(startDate.YEAR);
    month  = endDate.get(endDate.MONTH)-startDate.get(startDate.MONTH);
    day    = endDate.get(endDate.DATE)-startDate.get(startDate.DATE);
    hour   = endDate.get(endDate.HOUR_OF_DAY)-startDate.get(startDate.HOUR_OF_DAY);
    minute = endDate.get(endDate.MINUTE)-startDate.get(startDate.MINUTE);
    second = endDate.get(endDate.SECOND)-startDate.get(startDate.SECOND);
    millis = endDate.get(endDate.MILLISECOND)-startDate.get(startDate.MILLISECOND);
 
    if (millis < 0)
    {
      millis = millis + 1000;
      second = second - 1;
    }
    if (second < 0)
    {
      second = second + 60;
      minute = minute - 1;
    }
    if (minute < 0)
    {
      minute = minute + 60;
      hour = hour - 1;
    }
    if (hour < 0)
    {
      hour = hour + 24;
      day = day - 1;
    }
    if (day < 0)
    {
      day = day + startDate.getActualMaximum(startDate.DAY_OF_MONTH); // is the same as startDate.DATE
      month = month - 1;
    }
    if (month < 0)
    {
      month = month + 12;
      year = year - 1;
    }

    if (year > 0)
      result = year+" year";
    if (year > 1)
      result = result+"s ";
    else
      result = result+" ";
    if (month > 0)
      result = result+month+" month";
    if (month > 1)
      result = result+"s ";
    else
      result = result+" ";
    if (day > 0)
      result = result+day+" day";
    if (day > 1)
      result = result+"s ";
    else
      result = result+" ";
    if (hour > 0)
      result = result+hour+" hour";
    if (hour > 1)
      result = result+"s ";
    else
      result = result+" ";
    if (minute > 0)
      result = result+minute+" minute";
    if (minute > 1)
      result = result+"s ";
    else
      result = result+" ";
    if (second > 0)
      result = result+second+" second";
    if (second > 1)
      result = result+"s ";
    else
      result = result+" ";
    result = result+millis+" millisecond";
    if (millis > 1)
      result = result+"s ";
    else
      result = result+" ";
    return result;
  }


  /** Compute a new GregorianCalendar from a beginning date and a duration in days.
   *
   * @param startDate beginning date
   * @param durationInDays number of days to add to startDate.
   *
   * @return date corresponding to startDate+durationInDays
   *
   */
  public static GregorianCalendar addDays(GregorianCalendar startDate, int durationInDays)
  {
    GregorianCalendar date = (GregorianCalendar)startDate.clone();
    date.add(date.DATE, durationInDays);
    return date;
  }
}
