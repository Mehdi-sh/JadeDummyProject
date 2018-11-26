package content;

import java.io.Serializable;


public class TravelPackageObject implements Serializable {

	private String targetAutomobile;
	private String targetFlight;
	private String targetHotel;

	public TravelPackageObject(String automobile, String flight, String hotel){
		targetAutomobile = automobile;
		targetFlight = flight;
		targetHotel = hotel;
	}

	public String getAutomobile() {
		return targetAutomobile;
	}

	public String getFlight() {
		return targetFlight;
	}
	
	public String getHotel() {
		return targetHotel;
	}
}
