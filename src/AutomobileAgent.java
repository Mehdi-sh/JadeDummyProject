
import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.util.*;

public class AutomobileAgent extends Agent {
	// The catalogue of automobiles for rent (maps the title of a automobile to its renting price)
	private Hashtable automobileCatalogue;
	// The GUI by means of which the user can add books in the catalogue
	private AutomobileWebServiceGui automobileGui;

	// Put agent initializations here
	protected void setup() {
		// Create the catalogue
		automobileCatalogue = new Hashtable();

		// Create and show the GUI 
		automobileGui = new AutomobileWebServiceGui(this);
		automobileGui.showGui();

		// Register the automobile renting service in the yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("automobile-renting");
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

		// Add the behaviour serving renting orders from travel agents
		addBehaviour(new RentingOrdersServer());
	}

	protected void takeDown() {
		// Deregister from the yellow pages
		try {
			DFService.deregister(this);
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}
		// Close the GUI
		automobileGui.dispose();
		// Printout a dismissal message
		System.out.println("automobile-agent "+getAID().getName()+" terminating.");
	}

	/**
     This is invoked by the GUI when the user adds a new automobile for renting
	 */
	public void updateCatalogue(final String title, final int price) {
		addBehaviour(new OneShotBehaviour() {
			public void action() {
				automobileCatalogue.put(title, new Integer(price));
				System.out.println(title+" inserted into catalogue. Renting Price = "+price);
			}
		} );
	}

	/**
	   Inner class OfferRequestsServer.
	   This is the behaviour used by Automobile agents to serve incoming requests 
	   for offer from travel agents.
	   If the requested automobile is in the local catalogue the automobile agent replies 
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

				Integer price = (Integer) automobileCatalogue.get(title);
				if (price != null) {
					// The requested automobile is available for rent. Reply with the price
					reply.setPerformative(ACLMessage.PROPOSE);
					reply.setContent(String.valueOf(price.intValue()));
				}
				else {
					// The requested automobile is NOT available for rent.
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
	   Inner class RentingOrdersServer.
	   This is the behaviour used by automobile renting agents to serve incoming 
	   offer acceptances (i.e. renting orders) from travel agents.
	   The automobile agent removes the rented automobile from its catalogue 
	   and replies with an INFORM message to notify the travel agent that the
	   renting order has been sucesfully completed.
	 */
	private class RentingOrdersServer extends CyclicBehaviour {
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				// ACCEPT_PROPOSAL Message received. Process it
				String title = msg.getContent();
				ACLMessage reply = msg.createReply();

				Integer price = (Integer) automobileCatalogue.remove(title);
				if (price != null) {
					reply.setPerformative(ACLMessage.INFORM);
					System.out.println(title+" rented to agent "+msg.getSender().getName());
				}
				else {
					// The requested automobile has been rented to another buyer in the meanwhile .
					reply.setPerformative(ACLMessage.FAILURE);
					reply.setContent("not-available");
				}
				myAgent.send(reply);
			}
			else {
				block();
			}
		}
	}  // End of inner class RentingOrdersServer
}
