/**
 *  Import Url:   https://raw.githubusercontent.com/arnbme/hubitat/master/luxLighting.groovy
 *
 *  luxLighting App
 *  Functions:
 *
 *		1. Turn lights on and off based on illumination (Lux), HSM armstate, time of day
 *
 *  Copyright 2020 Arn Burkhoff
 *
 * 	Changes to Apache License
 *	4. Redistribution. Add paragraph 4e.
 *	4e. This software is free for Private Use. All derivatives and copies of this software must be free of any charges,
 *	 	and cannot be used for commercial purposes.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Jul 11, 2020 v0.0.8 Change base lux, date period and second Lux to input parameters vs hard coded
 *  Jul 10, 2020 v0.0.7 Add subscribe to a switch, setting light on for 10 minutes
 *							bool determines if Lux participates in light On decision, or switch forces On
 * 							Use luxHandler to turn light off as needed
 *  Jul 10, 2020 v0.0.6 Add bool that determines if Lux participates in Motion On decision, or Motion forces light on
 *  Jul 09, 2020 v0.0.5 Add subscribe to a motion sensor turning light on for 10 minutes
 * 							Use luxHandler to turn light off as needed
 *  Jul 08, 2020 v0.0.4 add optional Lux point for each globalLights
 *						eliminate use of state fields
 *						rewrite all on/off logic
 *  Jul 08, 2020 v0.0.3 Set state.currLux in hsmStatusHandler
 *						luxHandler clean up compare for On or Off, make it more understandable
 *  Jul 07, 2020 v0.0.2 Add Time input field and logic for lights off
 *  Jul 07, 2020 v0.0.1 Handle HSM status changes
 *						comment out sunset sunrise logic for now
 *  Jul 05, 2020 v0.0.0 Create with logic from RM lux lighing rule
 */

definition(
    name: "luxLighting",
    namespace: "arnbme",
    author: "Arn Burkhoff",
    description: "(${version()}) Lux Lighting app",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: "",
	importUrl: "https://raw.githubusercontent.com/arnbme/hubitat/master/luxLighting.groovy")

preferences {
    page(name: "mainPage")
	}

def version()
	{
	return "0.0.7";
	}

def mainPage()
	{
	dynamicPage(name: "mainPage", title: "(V${version()}) Lux Lighting Settings", install: true, uninstall: true)
		{
		section
			{
			input "globalDisable", "bool", required: true, defaultValue: false,
				title: "Disable All Functions. Default: Off/False"
			input "logDebugs", "bool", required: true, defaultValue: false,
				title: "Do debug logging. Shuts off after 30 minutes Default: Off/False"
			input "globalTestLux", "number", required: true, multiple: false, range: "1..10000",
				title: "Standard Lux point"
			input "globalDateLux", "number", required: false, multiple: false, range: "1..10000",submitOnChange: true,
				title: "Date based Lux point used between two dates (Optional) Date fields appear when entered."
			if (globalDateLux)
				{
				input "globalFromMonth", "enum", required: true, width: 15,title: "Starting Month", submitOnChange: true,
					options: ['January','February','March','April','May','June','July','August','September','October','November','December']
				switch (globalFromMonth)
					{
					case ('April'):
					case ('June'):
					case ('September'):
					case ('November'):
						input "globalFromDate", "enum", required: true, width: 4, title: "Starting date",
							options:[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,35,26,27,28,29,30]
					break
					case ('February'):
						input "globalFromDate", "enum", required: true, width: 4, title: "Starting date",
							options:[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,35,26,27,28]
					break
					default:
						input "globalFromDate", "enum", required: true, width: 4, title: "Starting date",
							options:[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,35,26,27,28,29,30,31]
					}
				paragraph ""
				input "globalToMonth", "enum", required: true, width: 15, title: "Ending Month", submitOnChange: true,
					options: ['January','February','March','April','May','June','July','August','September','October','November','December']
				switch (globalToMonth)
					{
					case ('April'):
					case ('June'):
					case ('September'):
					case ('November'):
						input "globalToDate", "enum", required: true, width: 4, title: "Ending date",
							options:[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,35,26,27,28,29,30]
					break
					case ('February'):
						input "globalToDate", "enum", required: true,width:4, title: "Ending date",
							options:[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,35,26,27,28]
					break
					default:
						input "globalTodate", "enum", required: true,width: 4, title: "Ending date",
							options:[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,35,26,27,28,29,30,31]
					}
				}
			input "globalLuxSensors", "capability.illuminanceMeasurement", required: true, multiple: true,
				title: "Lux sensors. When more than one, the average lux value is used"
			input "globalTimeOff", "time", title: "Optional: Turn off lighting devices at this time daily. Leave blank to ignore", required: false
			input "globalLights", "capability.switch", required: true, multiple: true, submitOnChange: true,
				title: "One or more Bulbs, Leds or Switches"

//			for each globalLights get a brightness and optional motion and switch sensors if active leave light on
			globalLights.each
				{
				if (it.hasCommand('setLevel'))
					{
					input "global${it.id}Dim", "number", required: false, multiple: false, range: "1..100",
						title: "${it.name}<br />Brightness Level 1 to 100, leave blank for ON with no level (Optional)"
					}
				input "global${it.id}Lux", "number", required: false, multiple: false, range: "1..8000",submitOnChange: true,
					title: "${it.name}<br />Lux On/Off point 1 to 8000, leave blank for app internal settings (Optional)"
				input "global${it.id}Motion", "capability.motionSensor", required: false, multiple: false,submitOnChange: true,
					title: "${it.name}<br />A Motion Sensor when active sets light On for 10 Minutes, status used during Off decision (Optional)"
				settingMotion="global${it.id}Motion"
				if (settings."$settingMotion")
					input "global${it.id}MotionFlag", "bool", required: false, defaultValue: false,
						title: "${it.name}<br />On/True: System or Device Lux participates in motion On decision<br />Off/False (Default): Ignore lux, force light to On<br />"
				input "global${it.id}Switch", "capability.switch", required: false, multiple: false,submitOnChange: true,
					title: "${it.name}<br />A Momentary Switch when set on sets light On for 10 Minutes(Optional)"
				settingSwitch="global${it.id}Switch"
				if (settings."$settingSwitch")
					input "global${it.id}SwitchFlag", "bool", required: false, defaultValue: false,
						title: "${it.name}<br />On/True: System or Device Lux participates in switch On decision<br />Off/False (Default): Ignore lux, force light to On<br />"
				}
			}
		}
	}

void installed() {
    log.info "Installed with settings: ${settings}"
	initialize()
}

void updated() {
    log.info "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

void initialize()
	{
	if(settings.logDebugs)
		runIn(1800,logsOff)			//turns off debug logging after 30 min
	else
		unschedule(logsOff)
	unschedule(timeOffHandler)		//clear any pending scheduled events
	if (globalDisable)
		{}
	else
		{
//		Create two program defined globals for start and end Lux date
		if (globalDateLux)
			{
			Map months = [January: '01',
			February: '02',
			March: '03',
			April: '04',
			May: '05',
			June: '06',
			July: '07',
			August: '08',
			September: '09',
			October: '10',
			November: '11',
			December: '12']
			def beginMMDD=""
			def endMMDD=""
			if (globalFromDate < '10')
				beginMMDD = months[globalFromMonth] + '0' + globalFromDate as String
			else
				beginMMDD= months[globalFromMonth]+globalFromDate as String
			if (globalToDate < '10')
				endMMDD = months[globalToMonth] + '0' + globalToDate as String
			else
				endMMDD= months[globalToMonth]+globalToDate as String
			app.updateSetting("globalFromMMDD",[value: beginMMDD,type:"string"])
			app.updateSetting("globalToMMDD",[value: endMMDD, type:"string"])
			}

		subscribe(globalLuxSensors, "illuminance", luxHandler)
		subscribe(location, "hsmStatus", luxHandler)
		if (globalTimeOff)
			schedule(globalTimeOff, timeOffHandler)
		globalLights.each
			{
			settingMotion="global${it.id}Motion"
			if (settings."$settingMotion")
				{
				subscribe (settings."$settingMotion", "motion.active", deviceHandler)
				}
			settingSwitch="global${it.id}Switch"
			if (settings."$settingSwitch")
				{
				subscribe (settings."$settingSwitch", "switch.on", deviceHandler)
				}
			}
		}
	}

void logsOff(){
//	stops debug logging
	log.info "luxLighting: debug logging disabled"
	app.updateSetting("logDebugs",[value:"false",type:"bool"])
}

void luxHandler(evt,forceOff=false,onlyLight=false)
	{
	if (settings.logDebugs) log.debug "luxLighting: luxHandler entered"
	def total = new Integer("0")
	def currLux = new Integer("0")
	def luxCount = new Integer("0")
	globalLuxSensors.each
		{
		luxCount++
		total+=it.currentValue('illuminance').intValueExact()
		}
	if (luxCount>1)
		currLux = total.intdiv(luxCount)
	else
		currLux = total
	if (settings.logDebugs) log.debug "currLux: $currLux ${currLux.class.name}"
	def appTestLux = globalTestLux  as Integer
	if (globalDateLux)
		{
		def mmdd = new Date().format( 'MMdd')	// mmdd is a text String, test accordingly
		if (settings.logDebugs) log.debug "luxHandler testing date $mmdd $globalFromMMDD and $globalToMMDD using globalDateLux: $globalDateLux"
//		log.debug "luxHandler ${mmdd.class.name} ${globalFromMMDD.class.name} ${globalToMMDD.class.name}"
		if (globalToMMDD >= globalFromMMDD)
			{
			if (mmdd >= globalFromMMDD && mmdd <= globalToMMDD)
				{
				if (settings.logDebugs) log.debug "luxHandler $mmdd is between $globalFromMMDD and $globalToMMDD using globalDateLux: $globalDateLux"
				appTestLux  = globalDateLux as Integer
				}
			}
		else
		if (mmdd >= globalToMMDD && mmdd <= globalFromMMDD)
			{}
		else
			{
			if (settings.logDebugs) log.debug "luxHandler $mmdd is between $globalFromMMDD and $globalToMMDD using globalDateLux: $globalDateLux"
			appTestLux = globalDateLux as Integer
			}
		}
	if (settings.logDebugs) log.debug "currLux: $currLux ${currLux.class.name} appTestLux: $appTestLux ${appTestLux.class.name}"

//	def sunRiseSet = getSunriseAndSunset(sunriseOffset: +45, sunsetOffset: -45)
//	if (settings.logDebugs) log.debug "sunRise+45 ${sunRiseSet.sunrise} sunSet-45 ${sunRiseSet.sunset} ${sunRiseSet.sunrise.class.name} now ${new Date()}"
//	if (!timeOfDayIsBetween(sunRiseSet.sunrise, sunRiseSet.sunset, new Date(), location.timeZone))
//		{
//		if (settings.logDebugs) log.debug "Not between sunrise+45 and sunset-45 due to sunset rule"
//		return
//		}

	def	settingDim=""
	def	settingLux=""
	def	settingMotion=""
	globalLights.each
		{
		if (onlyLight && onlyLight != it.id) //dont process this light
			{
			if (settings.logDebugs) log.debug "luxHandler ${it.name} skipped ${it.id} $onlyLight"
			}
		else
			{
			if (onlyLight)
				if (settings.logDebugs) log.debug "luxHandler ${it.name} processing onlylight ${it.id}"
			settingDim="global${it.id}Dim"
			settingLux="global${it.id}Lux"
			settingMotion="global${it.id}Motion"
			if (settings."$settingLux")
				testLux=settings."$settingLux"
			else
				testLux = appTestLux
			if (settings.logDebugs) log.debug "${it.name} currLux: $currLux testLux: $testLux forceOff: ${forceOff} hsmStatus: $location.hsmStatus"
			if (it.currentValue('switch') == 'off')
				{
				if (location.hsmStatus == 'armedNight' || forceOff)
					{
					if (settings.logDebugs) log.debug "leaving ${it.name} Off"
					}
				else
				if (testLux >= currLux)
					{
					if (settings."$settingDim")
						{
						if (settings.logDebugs) log.debug "doing setlevel ${it.name} ${it.id} ${settingDim}: " + settings."$settingDim"
						it.setLevel(settings."$settingDim", 5)
						}
					else
						{
						if (settings.logDebugs) log.debug "doing On ${it.name} ${it.id} ${settingDim} not found"
						it.on()
						}
					}
				}
			else
			if (testLux < currLux || location.hsmStatus == 'armedNight' || forceOff)		//bulb is on if we get here
				{
				if (settings."$settingMotion" && settings."$settingMotion".currentValue('motion') == 'active')
					{
					if (settings.logDebugs) log.debug "leaving ${it.name} On, $settingMotion is active"
					}
				else
					{
					if (settings.logDebugs) log.debug "doing off ${it.name} ${it.id}"
					it.off()
					}
				}
			}
		}
	}

void timeOffHandler()
	{
	if (settings.logDebugs) log.debug  "luxLighting timeOffHandler"
	luxHandler(true,true)
	}

void deviceHandler(evt)
	{
	def settingDevice=""
	def settingDim=""
	def deviceSensor = evt.getDevice()
	def deviceText='Switch'
	if (deviceSensor.hasCapability("MotionSensor"))
		deviceText='Motion'
	if (settings.logDebugs) log.debug  "luxLighting deviceHandler ${deviceSensor.name} ${deviceSensor.id} deviceText $deviceText"
	globalLights.find
		{
		settingDevice="global${it.id}${deviceText}"
		settingDeviceFlag="global${it.id}${deviceText}Flag"
		if (settings.logDebugs) log.debug "working with ${settingDevice}"
		if (settings."$settingDevice")
			{
			if (settings.logDebugs) log.debug "$deviceText occurred for light device ${it.name}"
			runIn (600, lightOff, [data:it])		//turn off in 10 minutes from last activity, pass the device object
			if (settings."$settingDeviceFlag")
				{
				if (settings.logDebugs) log.debug "deviceFlag says use Lux lighting for ${it.name}"
				luxHandler(false,false,it.id)
				}
			else
				{
				settingDim="global${it.id}Dim"
				if (settings."$settingDim")
					{
					if (settings.logDebugs) log.debug "luxLighting deviceHandler doing setlevel ${it.name} ${it.id} ${settingDim}: " + settings."$settingDim"
					it.setLevel(settings."$settingDim", 5)
					}
				else
					{
					if (settings.logDebugs) log.debug "luxLighting deviceHandler doing On ${it.name} ${it.id} ${settingDim} not found"
					it.on()
					}
				return true
				}
			}
		else
			return false
		}
	}

void lightOff(dvcObj)
	{
	if (settings.logDebugs) log.debug "luxLighting lightOff entered ${dvcObj.id} ${dvcObj.name}"
	luxHandler(false,false,dvcObj.id)		//light may or may not be turned off based on lux and system satus
	}