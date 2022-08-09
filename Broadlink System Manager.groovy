/*
Copyright 2022 - tomw

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

-------------------------------------------

Change history:

0.9.2 - tomw - Rename codes (supported in app).
0.9.0 - tomw - Initial release.

*/

definition(
    name: "Broadlink System Manager",
    namespace: "tomw",
    author: "tomw",
    description: "",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "")

preferences
{
    page(name: "mainPage")
    page(name: "pullCodesPage")
    page(name: "pushCodesPage")
    page(name: "codeManagementPage")
}

@Field deviceFilterString = "device.BroadlinkRemote"

def mainPage()
{
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true)
    {
        section("<b>Broadlink System Manager</b>")
        {
            href(page: "pullCodesPage", title: "<b>Save codes from virtual devices into this app.</b>")
            href(page: "pushCodesPage", title: "<b>Sync codes from this app out to virtual devices.</b>")
            href(page: "codeManagementPage", title: "<b>Manage saved codes and import new codes into this app.</b>")
        }

        section("<b>Configuration options:</b>")
        {
//			input name: "enableLogging", type: "bool", title: "Enable debug logging?", defaultValue: false, required: true
			if (settings.enableLogging)
				input "buttonDebugOff", "button", title: "Stop Debug Logging"
			else
				input "buttonDebugOn", "button", title: "Debug For 30 minutes"
        }
    }
}

def linkToMain()
{
    section
    {
        href(page: "mainPage", title: "<b>Return to previous page</b>", description: "")
    }
}

def cleanBreak(numBreaks)
{
    def breakSection = "<br>" * numBreaks
    section
    {
        paragraph(breakSection)
    }
}

def displayStatus(status)
{
    section
    {
        paragraph("<b>Status:</b> ${status} (ts = ${new Date().getTime()})")
    }
}

def pullCodesPage()
{
    dynamicPage(name: "pullCodesPage", title: "", install: true, uninstall: true)
    {
        def status = "Waiting for user input."

        if(actuallyPull)
        {
            app.updateSetting("actuallyPull", false)

            def codesOptions = collectCodesFromDevs()
            def codesToPull = [:]
            if(null != keysToPull) { codesToPull = codesOptions?.subMap(keysToPull) }

            // meaningful status by default, in case nothing was selected
            status = "No codes selected."

            codesToPull.each
            {
                addSavedCode(it.key, it.value)
                status = "Codes saved in to app."
            }
        }

        displayStatus(status)

        section
        {
            input(name:	"syncInDevs", type: deviceFilterString, title: "Select Broadlink Remotes to save codes from", multiple: true, required: false, submitOnChange: true)
            input(name: "keysToPull", type: "enum", title: "Select codes to save into this app", options: collectCodesFromDevs()?.keySet()?.sort(), multiple: true, required: false, offerAll:true, submitOnChange: true, width: 8)
//			if (settings.syncInDevs && settings.codesToPush)
			if (settings.syncInDevs)
				{
//            	input(name: "actuallyPull", type: "bool", title: "Press to proceed!", defaultValue: false, submitOnChange: true, width: 4)
				input "buttonActuallyPull", "button", title: "Press to Proceeed!"
				}
        }

        cleanBreak(1)

        linkToMain()
    }
}

def pushCodesPage()
{
    dynamicPage(name: "pushCodesPage", title: "", install: true, uninstall: true)
    {
        def status = "Waiting for user input."

        if(actuallyPush)
        {
            app.updateSetting("actuallyPush", false)

            Map codes = [:]
            // meaningful status by default, in case nothing was selected
            status = "No codes selected."

            if(null != codesToPush)
            {
                codes = allKnownCodesMap()?.subMap(codesToPush)
                pushCodesToDevs(codes)
                status = "Codes synced out to devices."
            }
        }

        displayStatus(status)

        section
        {
            input(name:	"syncOutDevs", type: deviceFilterString, title: "Select Broadlink Remotes to sync codes to from app", multiple: true, required: false, submitOnChange: true)
            input(name: "codesToPush", type: "enum", title: "Select codes to sync to these devices", options: allKnownCodesKeys(), multiple: true, required: false, offerAll:true, submitOnChange: true, width: 7)
//			if (settings.syncOutDevs && settings.codesToPush)
			if (settings.syncOutDevs)
				{
//            	input(name: "actuallyPush", type: "bool", title: "Press to proceed!", defaultValue: false, submitOnChange: true, width: 3)
				input "buttonActuallyPush", "button", title: "Press to Proceeed!"
				}
        }

        cleanBreak(1)

        linkToMain()
    }
}

def codeManagementPage()
{
    dynamicPage(name: "codeManagementPage", title: "", install: true, uninstall: true)
    {
        def status = "Waiting for user input."

        if(clearAllCodes)
        {
            app.updateSetting("clearAllCodes", false)
            clearSavedCodes()

            status = "Cleared all codes."
        }

        if(deleteSelectedCodes)
        {
            app.updateSetting("deleteSelectedCodes", false)
            codesToDelete.each
            {
                deleteSavedCode(it)
            }

            status = "Deleted selected codes."
        }

        if(renameSelectedCode)
        {
            app.updateSetting("renameSelectedCode", false)

            // default status, in case rename fails
            status = "Rename failed!"

            if(renameSavedCode(codeToRename, newCodeName))
            {
                status ="Renamed code \"${codeToRename}\" to \"${newCodeName}\""
            }
        }

        if(importEnteredCodes)
        {
            app.updateSetting("importEnteredCodes", false)
            importCodes(codesToImport)

            status = "Imported codes."
        }

        if(importProntoCodes)
        {
            app.updateSetting("importProntoCodes", false)

            // default status, in case conversion fails
            status = "Pronto import failed!"

            def code = convertProntoToBroadlink(prontoToImport)
            if(code)
            {
                addSavedCode(prontoName, code)
                status = "Imported Pronto code as ${prontoName}."
            }
        }

        displayStatus(status)

        section("<b>Delete saved codes</b>")
        {
            input(name: "codesToDelete", type: "enum", title: "Select saved codes to delete from app", options: allKnownCodesKeys(), multiple: true, required: false, offerAll:true)

//         input(name: "deleteSelectedCodes", type: "bool", title: "Delete selected codes", defaultValue: false, submitOnChange: true, width: 3)
            input "buttondeleteSelectedCodes", "button", title: "Delete selected codes from app"

//         input(name: "clearAllCodes", type: "bool", title: "Delete ALL codes from app", defaultValue: false, submitOnChange: true, width:3)
//			input "buttonclearAllCodes", "button", title: "Delete ALL codes from app"
        }

        section("<b>Rename saved codes</b>")
        {
            input(name: "codeToRename", type: "enum", title: "Select code to rename", options: allKnownCodesKeys(), multiple: false, required: false, width:4)
            input(name: "newCodeName", type: "text", title: "Rename to:", required: false, width:2)
//          input(name: "renameSelectedCode", type: "bool", title: "Rename selected code", defaultValue: false, submitOnChange: true)
//			if (codeToRename && newCodeName)
				input "buttonRenameSelectedCode", "button", title: "Rename selected code"
        }

        section("<b>Import hex codes</b>")
        {
            input(name: "codesToImport", type: "text", title: "Codes to import (in form of {name=2600...,name2=B200...})", required: false)
//         input(name: "importEnteredCodes", type: "bool", title: "Press to import entered codes", defaultValue: false, submitOnChange: true)
//			if (codesToImport)
				input "buttonimportEnteredCodes", "button", title: "Press to import entered codes"
        }

        section("<b>Import pronto codes</b>")
        {
            input(name: "prontoToImport", type: "text", title: "Pronto code to import (in form of \"0000 12AB\")", required: false, width: 8)
            input(name: "prontoName", type: "text", title: "Save as:", required: false, width: 4)
//         input(name: "importProntoCodes", type: "bool", title: "Press to import entered codes", defaultValue: false, submitOnChange: true)
//			if (prontoToImport && prontoName)
				input "buttonimportProntoCodes", "button", title: "Press to import Pronto codes"
        }

        cleanBreak(1)

        linkToMain()
    }
}

def updated()
{
    installed()
}

def installed()
{
    unsubscribe()
}

#include tomw.broadlinkHelpers

def collectCodesFromDevs()
{
    def combinedCodes = [:]
    syncInDevs.each
    {
        it.cacheCodesForApp(true)
        Map intCodes = new groovy.json.JsonSlurper().parseText(it.getDataValue("codes"))
        if(intCodes)
        {
            combinedCodes << intCodes
        }
        it.cacheCodesForApp(false)
    }

    return combinedCodes
}

def pushCodesToDevs(codes)
{
    if(!codes) { return }

    synchronized(codesSync)
    {
        def outStr = []
        // make this look like "name1=code1,name2=code2" for use with importCodes() on device
        codes.each { k, v -> outStr += ["${k}=${v}"]}

        outStr = outStr.join(',')
        logDebug("pushing codes to devices: ${outStr}")

        syncOutDevs.each
        {
            it.importCodes(outStr)
        }
    }
}

def logDebug(msg)
{
    if(enableLogging)
    {
        log.debug "${msg}"
    }
}
//	Process app buttons
void appButtonHandler(btn)
	{
	switch(btn)
		{
		case "buttonActuallyPush":
			app.updateSetting("actuallyPush",[value:"true",type:"bool"])
			break
		case "buttonActuallyPull":
			app.updateSetting("actuallyPull",[value:"true",type:"bool"])
			break
		case "buttonClearAllCodes":
			app.updateSetting("clearAllCodes",[value:"true",type:"bool"])
			break
		case "buttonRenameSelectedCode":
			app.updateSetting("renameSelectedCode",[value:"true",type:"bool"])
			break
		case "buttonimportEnteredCodes":
			app.updateSetting("importEnteredCodes",[value:"true",type:"bool"])
			break
		case "buttonimportProntoCodes":
			app.updateSetting("importProntoCodes",[value:"true",type:"bool"])
			break
		case "buttonDebugOff":
			debugOff()
			break
		case "buttonDebugOn":
			app.updateSetting("enableLogging",[value:"true",type:"bool"])
			runIn(1800,debugOff)		//turns off debug logging after 30 Minutes
			log.info "debug logging enabled"
			break
		default:
			log.debug btn+" processing logic not found"
			break
		}
	}

void debugOff(){
//	stops debug logging
	log.info "debug logging disabled"
	unschedule(debugOff)
	app.updateSetting("enableLogging",[value:"false",type:"bool"])
}
