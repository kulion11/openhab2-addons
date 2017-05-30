# openHAB NEEO Transport

This transport will allow openHAB things/items to be exposed to the NEEO Brain and allow the NEEO smart remote to control them.  
NEEO is a smart home solution that includes an IP based remote.  
More information can be found at [NEEO](neeo.com) or in the forums at [NEEO Planet](planet.neeo.com). 
**This transport was not developed by NEEO** - so please don't ask questions on the NEEO forums.  

The openHAB NEEO transport allows mapping of openHAB things/item to a NEEO device/capabilities (see Mappings below).  
Additionally, this transport provides full two-way communication between an openHAB instance and a NEEO Brain.

This transport compliments the NEEO binding by exposing a brain's devices to all other brains as well (allowing you to create multi-brain recipes).

## Features

The openHAB NEEO Transport will provide the following

* Automatic discovery of NEEO brains on the network and automatic registering of the openHAB as an SDK 
* A NEEO dashboard tile that will show the status of NEEO Brain connections and provide the ability to customize the mapping between openHAB things/item and NEEO device/capabilities
* Discovery of openHAB things on the NEEO app  
* Full two-way communcation between openHAB and brain.  Item changes in openHAB will appear in NEEO and vice-versa.

## openHAB Primary Address

This transport will use the primary address defined in openHAB to register itself as an SDK with the NEEO Brain (allowing the NEEO Brain to discover your things and to callback to openHAB to retrieve item values).  If you have multiple network interfaces on the machine that openHAB runs on or if forward actions are not being recieved, you'll likely need to set the primary address configuration field (PaperUI->Configuration->System->Network Settings->Primary Address)

## Mappings

For openHAB things/items to appear on the NEEO system, you must create mappings between openHAB and NEEO.  To accomplish this, go to the main openHAB dashboard (typically http://localhost:8080/start/index) and press the NEEO Transport dashboard tile.  This tile will open up a screen similar to the following

![Configuration](doc/dashboardmain.png)

### Tabs

There are a number of tabs

1.  Brains - will allow you to view the status of any connected brain (see Brains Tab below)
2.  Things - will allow you to manage the exposure of things to the brain (see Things Tab below)
 
Press the refresh button (next to the tab header) to refresh the information on the particular tab.

### Brains Tab

The Brains tab provides a listing of all NEEO brains that have been found, some information about them and their current status (connected or not).  You may press the "Blink" button to have the LED on the related NEEO brain blink (helpful if you have several brains and need to identify the specified brain). 

If your brain has not been discovered, you may manually enter the brain by pressing the "Add Brain" button.  You will be prompted for the Brain's IP address and once entered, the brain should be listed in the resulting table.

You may press the 'Disconnect' button to disconnect from the related Brain.  This will allow you to remove a stale (no longer used) Brain or remove the Brain to be rediscovered again (which may fix a communication issue with the brain). 
 
Please note the transport will properly handle IP address changes.  If the brain is assigned a new IP address, the transport will reconnect to the new IP address change when it receives the notification.  You can force the issue immediately by doing an "Add Brain" with the new IP address.

### Things Tab

The Things tab is where you specify the mappings.  

There are two types of devices listed here:

1.  openHAB things - any OH2 thing in the openHAB system and optionally (see configuration), any NEEO binding thing 
2.  virtual things - these are things you defined that can mix/match any OH1 or OH2 item

There are two types of items supported:

1.  Any valid openHAB item (OH1 or OH2).
2.  Trigger items - any trigger items defined by the openHAB thing or any trigger item that you add to an existing thing (regardless if it's an openHAB thing or a virtual thing).

Please review [NEEO SDK](https://github.com/NEEOInc/neeo-sdk) documentation before attempting to map.  This document describes some (but not all) of the requirements for NEEO things and capabilities.   The NEEO SDK document is written from the perspective of writing native plugins for NEEO but much of it's concepts apply here.  

Mappings consist of two parts:

1.  Mapping of an openHAB thing to a NEEO device or the creation of a new virtual thing
2.  Mapping of an openHAB item or trigger to one or more NEEO capabilities

Please see this screen shot for reference to the next two sections.  This screen show partially shows the setup of my Russound (whole house music system):
![Configuration](doc/dashboardthings.png)

#### Thing to Device

The first step in mapping is to map an openHAB thing to a NEEO device type.  You may either choose an existing openHAB item or create a new 'virtual' thing by pressing the "+ Virtual" button and assign a name to it.  

The following action can then be performed:

1.  Press the "+" (or "-") icon to see/hide the items for the thing
2.  Press the "i" button to see information about the thing (online status, etc)
3.  [Virtual things] Modify the device name  
4.  Specify or choose the NEEO device type for the openHAB thing
5.  [Virtual things] Press the "+" icon to add new items to the device
6.  Press the puzzle piece icon to add new triggers to the device
7.  Press the SAVE icon to save the mapping for the openHAB thing
8.  Press the REFRESH icon to refresh the mapping to the last saved state (discarding any pending changes)
9.  [openHAB things] Press the RESTORE icon to restore the mapping to it's original content (discarding any pending changes) 
10. [Virtual things] Press the DELETE icon to delete the thing
11. [things with triggers] Press the RULES icon to download an example .rules file for the triggers
12. [NON ACCESSORIE/LIGHT]  Specify device timings (see below)
  
The list of device types is unknown, must match those that NEEO expects (such as "ACCESSORIE", "AUDIO", etc) and you have the ability to enter the device type directly or select from a list of device types that have been discovered (although all may not be functional on the NEEO brain yet).   Please review the NEEO SDK documentation for hints on what device types are supported and what capabilities are supported by each device type.   

Please note that "ACCESSORIE" is not a misspelling.  That is how the NEEO brain expects it.  You may specify a type of "ACCESSORY" when defining a new type, but after saving - the type will switch back to "ACCESSORIE"

##### Device Timings (found on the properties page)

You can specify three device timings for any non ACCESSORIE and non LIGHT thing:

1. ON - specify how long (in milliseconds) it take the device to turn on
2. SWITCH - specify how long (in milliseconds) it takes the device to switch inputs
3. OFF - specify how long (in milliseconds) it takes the device to turn off

If the device does not have power state or doesn't support input switching, the numbers will be ignored.  

##### Device Capabilities (found on the properties page)

This following device capabilities are available:

1.  "Always On" - check if there is no power management for the device.  You do NOT need to specify any POWER buttons or POWER STATE sensor nor will the device be marked as 'stupid' 

##### Example

In the example screen shown above:

1.  The Russound AM/FM tuner was mapped to an "ACCESSORIE" NEEO type
2.  The Russound Great Room zone was mapped to an "AUDIO" device (to allow volume keys to work)

Any device type that is marked with any type will be visible to the NEEO App when searching for devices.

#### Format

When you have a text label, you can specify the text format to use and will be prefilled if the channel provides a default.  You can use any standard java string format for the value.  You may also provide a transformation format (in the same format as in .items file).  Example: "MAP(stuff.map):%s" will use the MAP tranformation file "stuff.map" to convert the value to a string. 

#### Items to Capabilities

The second step is to map openHAB items to one or more NEEO capabilities.  A NEEO capability can either be a virtual item on the screen or a hard button on the remote.

For each item, you may:

1.  Press the ADD icon to add a new mapping from the openHAB item (or DELETE icon to delete the mapping).
2.  Specify or choose the NEEO capability type for the openHAB item
3.  Specify the NEEO label (or hard button) for the mapping
4.  Optionally set the format or command for the mapping

At the time of this writing, the following NEEO capability types are supported:

1.  Text Label - this will simply take the toString() of the item value and optionally format via the Java String Format found in the "Format/Command" field before sending it to the NEEO brain.
2.  Button - this will create virtual button with the text from the "NEEO Label".  Upon pressing the button, the specified command in the "Format/Command" will be sent to the item (or ON will be sent if nothing has been specified).  Please note that you can also specify a hard button in the "NEEO Label" - in which case nothing will appear on the NEEO remote screen and the action will occur from the NEEO remote hard button.  You must specify all the hard buttons for a capability (as specified in the NEEO SDK documentation) for the button to work.  Example: if you only defined VOLUME DOWN but not VOLUME UP - the button will not work on the remote.  Likewise, which hard buttons are active or not additionally depends on the NEEO device type.
3.  Switch - this will create a virtual switch with the text from the "NEEO Label" and will send a ON or OFF command to the associated item.  Additionally, a switch can be bound to hard button pairs (the VOLUME keys, the POWER ON/OFF, the CHANNELS, etc).  The command that is sent is dependent on the KEYS chosen (POWER ON/OFF will send ON/OFF to the underlying item, all others will send an INCREASE/DECREASE). Similar to the "Button" type - please review the NEEO SDK documentation.    
4.  Slider - this will create a virtual slider that will send the associated value to the item.  The value sent will always be between 0 and 100.  
5.  ImageURL - this will create an image on the remote from the toString() of the item value (assuming it's a URL to an image).
6.  Sensor - this will create a sensor (non-visual) that can be used in recipes on the brain
7.  Power - this will create a powerstate sensor on the brain that can be used to stop/start the device.  NOTE: you MUST also assign a POWER OFF/POWER ON for this to work.

Please note the value for each of the hard buttons is specified in the NEEO SDK documentation.
 
The following chart shows what openHAB command types are supported for each NEEO Capability type

| NEEO Capability Type | openHAB Command Type                                                                                                         |
|----------------------|------------------------------------------------------------------------------------------------------------------------------|
| Text Label           | Any                                                                                                                          |
| Button               | Any non-readonly item                                                                                                        |
| Switch               | onofftype, increasedecreasetype, nextprevioustype,openclosedtype,playpausetype,rewindfastforwardtype,stopmovetype,updowntype |
| Slider               | percenttype, decimaltype                                                                                                     |
| ImageURL             | stringtype                                                                                                                   |
| Slider               | percenttype, decimaltype                                                                                                     |
| Power                | onofftype                                                                                                                    |
  
##### Power State Capability type

The power state capability type *REQUIRES a POWER ON and POWER OFF button to be assigned as well*.  This is a NEEO Brain requirement for the power state.  Sending ON to the power state item will start the device (similar to POWER ON button) and will stop the device on OFF.

##### Virtual Device Items

When you press the "+" icon (on the device) to add new items to the virtual device.  You'll be presented with a screen that will allow you to:

1.  Add a new item by pressing the "Add Item" button
2.  Delete all your items by pressing the "Delete All" button
3.  Import OH1 items from an .item files by pressing the "Import Item" button

The items section then provide a list of the items you have specified.  To specify a new item, simply click on the line in question - and then enter the item name.  All item names (OH1 and OH2) are shown in the dropdown (and will allow you to search/choose from that dropdown).  You will NOT be able to select an item not shown in this list - all items must be valid openHAB items.

You can delete individual items by pressing the "-" key next to it.

Press OK will add those items to the virtual thing.  Pressing cancel will simply dismiss the window without doing any action.

#### Trigger Items

Pressing the puzzle piece icon will add a new trigger item to the associated thing.  A trigger item will appear as a button on the NEEO Brain (with the specified label) and when pressed, will create a trigger event (with the optional payload) to be consumed by a rule.  You can use the RULES icon (on the device) to download an example .rules file with the rule definition for all trigger items defined.

##### Example

In the example screen above:

1.  The "KeyRelease Event" item was duplicated 3 times and bound to the hard buttons "CURSOR LEFT", "CURSOR RIGHT", "CURSOR UP" and "CURSOR DOWN".  Furthermore the command string "MenuLeft" will be sent to the "KeyRelease Event" item when the "CURSOR LEFT" hard button is pressed on the remote (right/up/down for the other buttons).
2.  The "Source" item was duplicated once and will create two virtual buttons on the NEEO screen labeled "AM/FM" and "PANDORA".  Pressing the "AM/FM" button the remote screen will send the value 1 to the "Source" item (2 if pressing the "PANDORA" button).
3.  The "Status" item was bound to both the power state and a switch assigned to the POWER ON/OFF hard buttons.  Pressing the POWER ON/OFF will send ON/OFF to the "Status" item.
4.  The "Volume" item was duplicated once.  The first instance is assigned to a text label that will then be formatted with the "VOLUME % of %%".  The second instance binds the item to a switch that uses the hard volume buttons on the remote.  When the volumes keys are pressed, a "INCREASE" or "DECREASE" will be sent to the item. 

### Transport Storage

All data used by the transport is stored under the "userdata/neeo" directory.  You may backup and restore these files as needed.  If you restore the directory, you'll likely need to restart openHAB for the new file contents to be applied.

There are two files being stored:

1.  discoveredbrains.json will contain the brains that are discovered or manually added from the 'brains' tab.  As brains are discovered, manually added or removed, this file will be updated.
2.  neeodefinitions.json will contain the device mappings defined on the 'things' tab.  As definitions are saved, this file will be updated.

## Configuration

After installing this add-on, you will find configuration options in the Paper UI under _Configuration->Services->IO->NEEO Transport_:

![Configuration](doc/cfg.png)

Alternatively, you can configure the settings in the file `conf/services/neeo.cfg`:

```
############################## openHAB NEEO Transport #############################

# A boolean value describing whether to expose all things/items
# by default or not.
# Default is 'false'.
#exposeAll=true|false

# Whether to automatically expose things from NEEO Binding
# Default is 'true'
#exposeNeeoBinding=true|false

# The maximum number of search results to return to the brain for any given 
# search request. 
# Default is 12 
#searchLimit=12

# The following is an advanced option - only specify this if the
# auto detection of the local IP address fails or incorrectly 
# identifies the ip address (as may happen if there are multiple
# network cards or the system is a VMWare host).
# 
# This IP address is given to the NEEO brain to form callbacks
# to this transport and must be reachable from the NEEO brain.
# 
# Leave blank to attempt to auto-detect.
#localIpAddress=192.168.1.101

# The interval (in seconds) to check the status of the brain to determine if the
# brain is reachable or not
# Default is 10 
#checkStatusInterval=10
```
