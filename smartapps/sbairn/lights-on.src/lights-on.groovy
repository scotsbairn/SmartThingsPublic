/**
 *  Lights On
*/


// Automatically generated. Make future change here.
definition(
    name: "Lights On",
    namespace: "sbairn",
    author: "sbairn@gmail.com",
    description: "Taken from craig.k.lyons@gmail.com, this App allows you to select a dimmer and change the setting to a defined brightness when pressed.",
    category: "",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/light_outlet.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/light_outlet@2x.png"
)

preferences {
	section("Which light to turn on:") {
		input "switches0", "capability.switch", multiple: true, required: false
	}
	section("Which light to set the mood on:") {
		input "switches1", "capability.switchLevel", multiple: true, required: false
	}
    section("What Level to set at..."){
    	input "lvl1", "number", required: false
    }
	section("Which light to set the mood on:") {
		input "switches2", "capability.switchLevel", multiple: true, required: false
	}
    section("What Level to set at..."){
    	input "lvl2", "number", required: false
    }
}

def installed()
{
	subscribe(app, appTouch)
}

def updated()
{
	unsubscribe()
	subscribe(app, appTouch)
}


def appTouch(evt) {
	log.info evt.value
    if(switches0!=null) {
        log.debug "turn on lights 0"
        switches0.on()
    }
    if(switches1!=null && lvl1!=null) {
        log.debug "turn on lights 1"
        switches1.setLevel(lvl1.value)
    }
    if(switches2!=null && lvl2!=null) {
        log.debug "turn on lights 2"
        switches2.setLevel(lvl2.value)
    }
}


