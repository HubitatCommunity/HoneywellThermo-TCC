# HoneywellThermo-TCC

This works by contacting Honeywell over the Internet using your Honeywell ID/PW and Thermostat ID. Yes, CLOUD.<p>

If you never setup TCC, then you won't have that info.<p>

https://www.mytotalconnectcomfort.com/portal/<p>

Create an account if you don't have one.<p>


<b>In Hubitat:</b><p>

Drivers Code<br>
<ul><li>New Driver</li>
<li>(paste in code from this repo)</li>
  <li>Save</li></ul><p>

Device<br>
<ul><li>New virtual device</li>
<li>(give it a name)</li>
<li>Type:(find user code at bottom)</li>
<li>save</li></ul><p>

Find the new device<br>
Find Preferences down page<br>
Enter name/password<br>
Enter Device ID <br>
Save<p>

The Thermostat ID is found as the last 6 or 7 digits on the end of the TCC URL after login. 
