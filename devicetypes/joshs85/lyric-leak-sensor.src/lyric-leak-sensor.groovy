/**
 *  Lyric Leak Sensor
 *
 *  Copyright 2017 Joshua Spain
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
metadata {
	definition (name: "Lyric Leak Sensor", namespace: "joshs85", author: "Joshua Spain") {
		capability "Water Sensor"
		capability "Sensor"
		capability "Battery"
        capability "Temperature Measurement"
        capability "Temperature Alarm"
		capability "Health Check"
        capability "polling"
        capability "powerSource"
        capability "refresh"
        capability "relativeHumidityMeasurement"
        
        attribute "deviceAlive", "enum", ["true", "false"]
	}
    
	tiles(scale: 2) {
		multiAttributeTile(name:"water", type: "generic", width: 6, height: 4){
			tileAttribute ("device.water", key: "PRIMARY_CONTROL") {
				attributeState "dry", label: "Dry", icon:"st.alarm.water.dry", backgroundColor:"#ffffff"
				attributeState "wet", label: "Wet", icon:"st.alarm.water.wet", backgroundColor:"#00A0DC"
			}
		}
		standardTile("temperatureAlarm", "device.temperatureAlarm", width: 2, height: 2) {
			state "normal", icon:"st.alarm.temperature.normal", backgroundColor:"#ffffff"
			state "freezing", icon:"st.alarm.temperature.freeze", backgroundColor:"#00A0DC"
			state "overheated", icon:"st.alarm.temperature.overheat", backgroundColor:"#e86d13"
		}
		valueTile("temperature", "device.temperature", width: 2, height: 2) {
            state("temperature", label:'${currentValue}°',
                backgroundColors:[
							// Celsius
							[value: 0, color: "#153591"],
							[value: 7, color: "#1e9cbb"],
							[value: 15, color: "#90d2a7"],
							[value: 23, color: "#44b621"],
							[value: 28, color: "#f1d801"],
							[value: 35, color: "#d04e00"],
							[value: 37, color: "#bc2323"],
							// Fahrenheit
							[value: 40, color: "#153591"],
							[value: 44, color: "#1e9cbb"],
							[value: 59, color: "#90d2a7"],
							[value: 74, color: "#44b621"],
							[value: 84, color: "#f1d801"],
							[value: 95, color: "#d04e00"],
							[value: 96, color: "#bc2323"]
					]
            )
        }
        valueTile("humidity", "device.humidity", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
            state "humidity", label:'${currentValue}%', icon:"st.Weather.weather12"
        }
		valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
			state "battery", label:'${currentValue}% battery', unit:""
		}
        standardTile("buzzerMuted", "device.buzzerMuted",inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "true", icon:"st.quirky.spotter.quirky-spotter-sound-off", label:'Buzzer Muted'
            state "false", icon:"st.quirky.spotter.quirky-spotter-sound-on", label:'Buzzer'
        }
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
		main (["water"])
		details(["water", "temperatureAlarm", "temperature", "humidity", "battery", "refresh", "buzzerMuted"])
}

	simulator {
		// TODO: define status and reply messages here
	}
}

void installed() {
    // The device refreshes every 1 minutes by default so if we miss 3 refreshes we can consider it offline
    sendEvent(name: "checkInterval", value: 60 * 3, data: [protocol: "cloud"], displayed: false)
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
}

def refresh() {
	log.debug "refresh called"
	poll()
}

void poll() {
	log.debug "Executing 'poll' using parent SmartApp"
	parent.pollChildren()

}

def generateEvent(Map results) {
  results.each { name, value ->
    sendEvent(name: name, value: value)
  }
  return null
}
