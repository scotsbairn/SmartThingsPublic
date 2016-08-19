/**
 *  Smart Nightlight
 *
 *  Original Author: Chrisb
 *
 */
definition(
  name: "Smart Nightlight",
	   namespace: "sbairn",
	   author: "sbairn",
	   description: "Turns on lights when it's dark and motion is detected.",
	   category: "Convenience",
	   iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/light_motion-outlet-luminance.png",
	   iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/light_motion-outlet-luminance@2x.png"
)

preferences {
  section("Enabled"){
        input(name: "isEnabledBool", type: "bool", title: "Enabled ?")
  }

  section("Control these lights..."){
    input "lights", "capability.switch", multiple: true
  }
  section("Turning on when it's dark and there's movement..."){
    input "motionSensors", "capability.motionSensor", title: "Where?", multiple: true
  }
  section("And then off when it's light or there's been no movement for..."){
    input "delayMinutes", "number", title: "Minutes?"
  }
  section ("Sunrise offset (optional)...") {
    input "sunriseOffsetValue", "text", title: "HH:MM", required: false
  }
  section ("Sunset offset (optional)...") {
    input "sunsetOffsetValue", "text", title: "HH:MM", required: false
  }
  section ("Zip code (optional, defaults to location coordinates when location services are enabled)...") {
    input "zipCode", "text", required: false
  }

}



def installed() {
  initialize()
}

def updated() {
  unsubscribe()
  unschedule()
  initialize()
}

def initialize() {
    subscribe(motionSensors, "motion", motionHandler)

    // subscribe to the switch events
    subscribe(lights, "switch.on", turnedOn)
    subscribe(lights, "switch.off", turnedOff)

    state.currentState="idle"  
    state.debugInfo=""

    log.debug "sensors: $motionSensors" + motionSensors.currentValue("motion")
    log.debug "lights: $lights" + lights.currentValue("switch")
    
  	if(isEnabledBool!=null) {
    	log.debug "smart night light enabled $isEnabledBool"
        if(isEnabledBool) {
         	log.debug "smart night light on"
        } else {
	       	log.debug "smart night light off"
        }
	}
}

def turnedOn(evt) {
    // only go to manual if we were in the idle state
    if(state=="idle") {
        log.debug "switch turned on, moving to manual mode"
        state.currentState="manual"
    }
}
    

def turnedOff(evt) {
    log.debug "switch turned off, moving to idle mode"
    state.currentState="idle"
}
 
   
def motionHandler(evt) {
    log.debug "$evt.name: $evt.value"

    def lightsOn=lights.currentValue("switch").contains("on")
    def motion=motionSensors.currentValue("motion").contains("active")

    log.debug "motionHandler: switches:" + lights.currentValue("switch") + " motion: " + motionSensors.currentValue("motion")
    log.debug "motionHandler: lightsOn $lightsOn motion $motion enabled $isEnabledBool state $state"

    if(motion) {
      if(isEnabledBool!=null && isEnabledBool) {
		if(state.currentState == "idle") {
		  if(dark()) {
		    log.debug "motion detected, current state idle, dark, turn on lights, move to active state"
			state.currentState = "active"
		    lights.on()
            state.debugInfo="motion, turn on state:" + motionSensors.currentValue("motion")
		  } else {
			log.debug "motion detected, however not dark, do nothing"
            state.debugInfo="motion, but not dark"
		  }
		} else {
		  log.debug "motion but not idle"
          state.debugInfo="motion, already on"
		}
	  } else {
		log.debug "motion lights are disabled in app preferences"
        state.debugInfo="motion, but disabled"
      }
    } else {
      if(state.currentState == "active") {
	    log.debug "scheduled off time $delayMinutes"
        state.debugInfo="no motion, schedule off"
	    runIn(delayMinutes*60, turnOffHandler)
      } else {
	    log.debug "no motion but state not active"
        state.debugInfo="no motion, but not active"
      }
    }
    log.debug "new state: $state"
}


def turnOffHandler() {
    log.debug "turnOffHandler"
    state.debugInfo="turnOffHandler $state.currentState"

    if(state.currentState == "active") {
        log.debug "turning off lights"
        lights.off()
        state.currentState == "idle"
    }
}

def astroCheck() {
  def sunriseTimeOffset=(sunriseOffsetValue==null) ? 0 : timeOffset(sunriseOffsetValue)
  def sunsetTimeOffset=(sunsetOffsetValue==null) ? 0 : timeOffset(sunsetOffsetValue)
  def s = getSunriseAndSunset(zipCode: zipCode, sunriseOffset: sunriseTimeOffset, sunsetOffset: sunsetTimeOffset)
  state.riseTime = s.sunrise.time
  state.setTime = s.sunset.time
  log.debug "rise: ${new Date(state.riseTime)}($state.riseTime), set: ${new Date(state.setTime)}($state.setTime)"
}

def dark() {
    // check the sunrise/sunset times
    astroCheck()
  
    def t = now()
    def e = (t < state.riseTime) || (t > state.setTime)
    log.debug "dark check: $t $state $e"
    return (t < state.riseTime) || (t > state.setTime)
}

