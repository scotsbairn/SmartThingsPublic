/**
 *  Good Night Switches
 *
 *  Copyright 2015 Eric Chesters
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
 */
definition(
    name: "No Motion Turn Off Switches",
    namespace: "sbairn",
    author: "Eric Chesters",
    description: "Smart turn things off at night",
    category: "",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
    section("Enabled") {
        input(name: "isEnabledBool", type: "bool", title: "Enabled ?")
    }    
    section("Time window") {
        input "timeStart", "time", title: "Start time"
        input "timeEnd", "time", title: "End time"
    }
    section("Control these switches..."){
        input "turnOff", "capability.switch", multiple: true
    }
    section("Turn off when there's no movement..."){
        input "motionSensors", "capability.motionSensor", title: "Where?", multiple: true
    }
    section("For..."){
        input "delayMinutes", "number", title: "Minutes?"
    }
     

}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
    unschedule()

	initialize()
}

def initialize() {

	state.active=false

    schedule(timeStart, startTimeReached)
    schedule(timeEnd, endTimeReached)

    subscribe(motionSensors, "motion", motionHandler)

    if(isEnabledBool!=null && isEnabledBool) {
        def timeOfDay_start = timeToday(timeStart).time
        def timeOfDay_end = timeToday(timeEnd).time
        if(timeOfDay_end<timeOfDay_start) {
            timeOfDay_end=(timeToday(timeEnd)+1).time
        }
        def currTime = now()
    
        log.debug "times: $timeOfDay_start $timeOfDay_end $currTime"

        if((currTime>timeOfDay_start) && (currTime<timeOfDay_end)) {
            log.debug "within window, start"
            state.active=true
            handleEvent()           
        }

    }
}

def startTimeReached(evt) {
    log.debug "start time reached: $state"
    if(isEnabledBool!=null && isEnabledBool) {
        state.active=true
        handleEvent()
    }
}


def endTimeReached(evt) {
    // we've reached the end window, make sure we are not active
    log.debug "end time reached: $state"
    if(state.active) {
        state.active=false
    }
}


def motionHandler(evt) {
    log.debug "$evt"
    handleEvent()
}


def handleEvent() {

    if(!state.active) {
        return
    }

    def switchesOn=turnOff.currentValue("switch").contains("on")    

    if(switchesOn) {
        def motionState=motionSensors.currentValue("motion")
        def motionActive=motionState.contains("active")
        
        log.debug "motion: $motionState $motionActive"

        if(motionActive) {
            // cancel any pending timerEvent
            log.debug "motion detected, cancel timerEvent"
            unschedule("timerEvent")            
        } else {
            log.debug "no motion detected, schedule a timerEvent"
            runIn(delayMinutes*60,timerEvent)            
        }
    } else {
        log.debug "all switches have been turned off, we are done for now"
        unschedule("timerEvent") 
        state.active=false
    }
}

def timerEvent() {
    log.debug "timer event fired"

    if(state.active) {
        log.debug "turn the switches off and return to non-active: $turnOff"
        turnOff.off()
        state.active=false        
    }
}

