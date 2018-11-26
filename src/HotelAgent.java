
import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.util.*;

public class HotelAgent extends Agent {
	// The catalogue of hotel for booking (maps the title of a hotel to its price)
	private Hashtable hotelCatalogue;
	// The GUI by means of which the user can add hotels in the catalogue
	private HotelWebServiceGui hotelGui;

	// Put agent initializations here
	protected void setup() {
		// Create the catalogue
		hotelCatalogue = new Hashtable();

		// Create and show the GUI 
		hotelGui = new HotelWebServiceGui(this);
		hotelGui.showGui();

		// Register the hotel-booking service in the yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("hotel-booking");
		sd.setName("Traveling_Industry_MAS");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}

		// Add the behaviour serving queries from travel agents
		addBehaviour(new OfferRequestsServer());

		// Add the behaviour serving booking orders from travel agents
		addBehaviour(new BookingOrdersServer());
	}

	// Put agent clean-up operations here
	protected void takeDown() {
		// Deregister from the yellow pages
		try {
			DFService.deregister(this);
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}
		// Close the GUI
		hotelGui.dispose();
		// Printout a dismissal message
		System.out.println("Hotel-agent "+getAID().getName()+" terminating.");
	}

	/**
     This is invoked by the GUI when the user adds a new hotel for booking
	 */
	public void updateCatalogue(final String title, final int price) {
		addBehaviour(new OneShotBehaviour() {
			public void action() {
				hotelCatalogue.put(title, new Integer(price));
				System.out.println(title+" inserted into catalogue. Booking price = "+price);
			}
		} );
	}

	/**
	   Inner class OfferRequestsServer.
	   This is the behaviour used by hotel-booking agents to serve incoming requests 
	   for offer from travel agents.
	   If the requested book is in the local catalogue the hotel agent replies 
	   with a PROPOSE message specifying the price. Otherwise a REFUSE message is
	   sent back.
	 */
	private class OfferRequestsServer extends CyclicBehaviour {
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				// CFP Message received. Process it
				String title = msg.getContent();
				ACLMessage reply = msg.createReply();

				Integer price = (Integer) hotelCatalogue.get(title);
				if (price != null) {
					// The requested hotel is available for booking. Reply with the price
					reply.setPerformative(ACLMessage.PROPOSE);
					reply.setContent(String.valueOf(price.intValue()));
				}
				else {
					// The requested hotel is NOT available for booking.
					reply.setPerformative(ACLMessage.REFUSE);
					reply.setContent("not-available");
				}
				myAgent.send(reply);
			}
			else {
				block();
			}
		}
	}  // End of inner class OfferRequestsServer

	/**
	   Inner class BookingOrdersServer.
	   This is the behaviour used by Book-seller agents to serve incoming 
	   offer acceptances (i.e. booking orders) from travel agents.
	   The seller agent removes the booked hotels from its catalogue 
	   and replies with an INFORM message to notify the travel that the
	   purchase has been sucesfully completed.
	 */
	private class BookingOrdersServer extends CyclicBehaviour {
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				// ACCEPT_PROPOSAL Message received. Process it
				String title = msg.getContent();
				ACLMessage reply = msg.createReply();

				Integer price = (Integer) hotelCatalogue.remove(title);
				if (price != null) {
					reply.setPerformative(ACLMessage.INFORM);
					System.out.println(title+" booked to agent "+msg.getSender().getName());
				}
				else {
					// The requested hotel has been booked to another travel agent in the meanwhile .
					reply.setPerformative(ACLMessage.FAILURE);
					reply.setContent("not-available");
				}
				myAgent.send(reply);
			}
			else {
				block();
			}
		}
	}  // End of inner class BookingOrdersServer
}
