
import content.TravelPackageObject;
import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

public class TravelAgent extends Agent {

	// The title of the Automobile to rent
	private String targetAutomobileTitle;
	// The title of the flight to buy
	private String targetFlightTitle;
	// The title of the Hotel to book
	private String targetHotelTitle;

	private boolean a = false;
	private boolean b = false;
	private boolean c = false;

	private int counter = 0;

	ACLMessage msg;

	private int bestAutomobilePrice;  // The best offered price
	private int bestFlightPrice;  // The best offered price
	private int bestHotelPrice;  // The best offered price

	// The list of known automobile agents
	private AID[] automobileAgents;
	// The list of known flight agents
	private AID[] flightAgents;
	// The list of known hotel agents
	private AID[] hotelAgents;	

	protected void setup() {

		// Register the travel broker service in the yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("travel-broker");
		sd.setName("Traveling_Industry_MAS");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}


		// Add a TickerBehaviour that schedules a request to seller agents every minute
		addBehaviour(new TickerBehaviour(this, 60000) {
			protected void onTick() {
				// Update the list of automobile agents
				DFAgentDescription template = new DFAgentDescription();
				ServiceDescription sd = new ServiceDescription();
				sd.setType("automobile-renting");
				template.addServices(sd);
				try {
					DFAgentDescription[] result = DFService.search(myAgent, template); 
					System.out.println("Found the following automobile agents:");
					automobileAgents = new AID[result.length];
					for (int i = 0; i < result.length; ++i) {
						automobileAgents[i] = result[i].getName();
						System.out.println(automobileAgents[i].getName());
					}
				}
				catch (FIPAException fe) {
					fe.printStackTrace();
				}

				// Update the list of flight agents
				DFAgentDescription template1 = new DFAgentDescription();
				ServiceDescription sd1 = new ServiceDescription();
				sd1.setType("flight-selling");
				template1.addServices(sd1);
				try {
					DFAgentDescription[] result = DFService.search(myAgent, template1); 
					System.out.println("Found the following flight agents:");
					flightAgents = new AID[result.length];
					for (int i = 0; i < result.length; ++i) {
						flightAgents[i] = result[i].getName();
						System.out.println(flightAgents[i].getName());
					}
				}
				catch (FIPAException fe) {
					fe.printStackTrace();
				}

				// Update the list of hotel agents
				DFAgentDescription template2 = new DFAgentDescription();
				ServiceDescription sd2 = new ServiceDescription();
				sd2.setType("hotel-booking");
				template2.addServices(sd2);
				try {
					DFAgentDescription[] result = DFService.search(myAgent, template2); 
					System.out.println("Found the following hotel agents:");
					hotelAgents = new AID[result.length];
					for (int i = 0; i < result.length; ++i) {
						hotelAgents[i] = result[i].getName();
						System.out.println(hotelAgents[i].getName());
					}
				}
				catch (FIPAException fe) {
					fe.printStackTrace();
				}

				// Add the behaviour serving queries from user agents
				myAgent.addBehaviour(new UserRequestsServer());



				// Perform the request
				myAgent.addBehaviour(new AutomobileRequestPerformer());
				myAgent.addBehaviour(new FlightRequestPerformer());
				myAgent.addBehaviour(new HotelRequestPerformer());

				myAgent.addBehaviour(new InformUserAgent());

			}
		} );
	}


	// Put agent clean-up operations here
	protected void takeDown() {
		// Printout a dismissal message
		System.out.println("Travel-agent "+getAID().getName()+" terminating.");
	}

	private class UserRequestsServer extends CyclicBehaviour {
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
			ACLMessage msg1 = myAgent.receive(mt);
			if (msg1 != null) {

				msg = msg1;
				// CFP Message received. Process it
				TravelPackageObject travelPackage;
				try {
					travelPackage = (TravelPackageObject) msg.getContentObject();
					targetAutomobileTitle = travelPackage.getAutomobile();
					targetFlightTitle = travelPackage.getFlight();
					targetHotelTitle = travelPackage.getHotel();
				} catch (UnreadableException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
			else {
				block();
			}
		}
	}

	private class AutomobileRequestPerformer extends Behaviour {
		private AID bestSeller; // The agent who provides the best offer 

		private int repliesCnt = 0; // The counter of replies from seller agents
		private MessageTemplate mt; // The template to receive replies
		private int step = 0;

		public void action() {
			switch (step) {
			case 0:
				// Send the cfp to all sellers
				ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
				for (int i = 0; i < automobileAgents.length; ++i) {
					cfp.addReceiver(automobileAgents[i]);
				} 
				cfp.setContent(targetAutomobileTitle);
				cfp.setConversationId("automobile-renting");
				cfp.setReplyWith("cfp"+System.currentTimeMillis()); // Unique value
				myAgent.send(cfp);
				// Prepare the template to get proposals
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("automobile-renting"),
						MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
				step = 1;
				break;
			case 1:
				// Receive all proposals/refusals from seller agents
				ACLMessage reply = myAgent.receive(mt);
				if (reply != null) {
					// Reply received
					if (reply.getPerformative() == ACLMessage.PROPOSE) {
						// This is an offer 
						int price = Integer.parseInt(reply.getContent());
						if (bestSeller == null || price < bestAutomobilePrice) {
							// This is the best offer at present
							bestAutomobilePrice = price;
							bestSeller = reply.getSender();
						}
					}
					repliesCnt++;
					if (repliesCnt >= automobileAgents.length) {
						// We received all replies
						step = 2; 
					}
				}
				else {
					block();
				}
				break;
			case 2:
				// Send the purchase order to the seller that provided the best offer
				ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
				order.addReceiver(bestSeller);
				order.setContent(targetAutomobileTitle);
				order.setConversationId("automobile-renting");
				order.setReplyWith("order"+System.currentTimeMillis());
				myAgent.send(order);
				// Prepare the template to get the purchase order reply
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("automobile-renting"),
						MessageTemplate.MatchInReplyTo(order.getReplyWith()));
				step = 3;
				break;
			case 3:      
				// Receive the purchase order reply
				reply = myAgent.receive(mt);
				if (reply != null) {
					// Purchase order reply received
					if (reply.getPerformative() == ACLMessage.INFORM) {
						// Purchase successful. We can terminate
						System.out.println(targetAutomobileTitle+" successfully rented from agent "+reply.getSender().getName());
						System.out.println("Price = "+bestAutomobilePrice);
						a = true;
					}
					else {
						System.out.println("Attempt failed: requested automobile already rented");
					}

					step = 4;
				}
				else {
					block();
				}
				break;
			}        
		}

		public boolean done() {
			if (step == 2 && bestSeller == null) {
				System.out.println("Attempt failed: "+targetAutomobileTitle+" not available for renting");
			}
			return ((step == 2 && bestSeller == null) || step == 4);
		}
	}  // End of inner class AutomobileRequestPerformer

	private class FlightRequestPerformer extends Behaviour {
		private AID bestSeller; // The agent who provides the best offer 

		private int repliesCnt = 0; // The counter of replies from seller agents
		private MessageTemplate mt; // The template to receive replies
		private int step = 0;

		public void action() {
			switch (step) {
			case 0:
				// Send the cfp to all sellers
				ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
				for (int i = 0; i < flightAgents.length; ++i) {
					cfp.addReceiver(flightAgents[i]);
				} 
				cfp.setContent(targetFlightTitle);
				cfp.setConversationId("flight-booking");
				cfp.setReplyWith("cfp"+System.currentTimeMillis()); // Unique value
				myAgent.send(cfp);
				// Prepare the template to get proposals
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("flight-booking"),
						MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
				step = 1;
				break;
			case 1:
				// Receive all proposals/refusals from seller agents
				ACLMessage reply = myAgent.receive(mt);
				if (reply != null) {
					// Reply received
					if (reply.getPerformative() == ACLMessage.PROPOSE) {
						// This is an offer 
						int price = Integer.parseInt(reply.getContent());
						if (bestSeller == null || price < bestFlightPrice) {
							// This is the best offer at present
							bestFlightPrice = price;
							bestSeller = reply.getSender();
						}
					}
					repliesCnt++;
					if (repliesCnt >= flightAgents.length) {
						// We received all replies
						step = 2; 
					}
				}
				else {
					block();
				}
				break;
			case 2:
				// Send the purchase order to the seller that provided the best offer
				ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
				order.addReceiver(bestSeller);
				order.setContent(targetFlightTitle);
				order.setConversationId("flight-booking");
				order.setReplyWith("order"+System.currentTimeMillis());
				myAgent.send(order);
				// Prepare the template to get the purchase order reply
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("flight-booking"),
						MessageTemplate.MatchInReplyTo(order.getReplyWith()));
				step = 3;
				break;
			case 3:      
				// Receive the booking order reply
				reply = myAgent.receive(mt);
				if (reply != null) {
					// Booking order reply received
					if (reply.getPerformative() == ACLMessage.INFORM) {
						// Booking successful. We can terminate
						System.out.println(targetFlightTitle+" successfully booked from agent "+reply.getSender().getName());
						System.out.println("Price = "+bestFlightPrice);
						b = true;
					}
					else {
						System.out.println("Attempt failed: requested flight already booked.");
					}

					step = 4;
				}
				else {
					block();
				}
				break;
			}        
		}

		public boolean done() {
			if (step == 2 && bestSeller == null) {
				System.out.println("Attempt failed: "+targetFlightTitle+" not available for sale");
			}
			return ((step == 2 && bestSeller == null) || step == 4);
		}
	}  // End of inner class FlightRequestPerformer

	private class HotelRequestPerformer extends Behaviour {
		private AID bestSeller; // The agent who provides the best offer 

		private int repliesCnt = 0; // The counter of replies from seller agents
		private MessageTemplate mt; // The template to receive replies
		private int step = 0;

		public void action() {
			switch (step) {
			case 0:
				// Send the cfp to all sellers
				ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
				for (int i = 0; i < hotelAgents.length; ++i) {
					cfp.addReceiver(hotelAgents[i]);
				} 
				cfp.setContent(targetHotelTitle);
				cfp.setConversationId("Hotel-booking");
				cfp.setReplyWith("cfp"+System.currentTimeMillis()); // Unique value
				myAgent.send(cfp);
				// Prepare the template to get proposals
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("Hotel-booking"),
						MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
				step = 1;
				break;
			case 1:
				// Receive all proposals/refusals from seller agents
				ACLMessage reply = myAgent.receive(mt);
				if (reply != null) {
					// Reply received
					if (reply.getPerformative() == ACLMessage.PROPOSE) {
						// This is an offer 
						int price = Integer.parseInt(reply.getContent());
						if (bestSeller == null || price < bestHotelPrice) {
							// This is the best offer at present
							bestHotelPrice = price;
							bestSeller = reply.getSender();
						}
					}
					repliesCnt++;
					if (repliesCnt >= hotelAgents.length) {
						// We received all replies
						step = 2; 
					}
				}
				else {
					block();
				}
				break;
			case 2:
				// Send the purchase order to the seller that provided the best offer
				ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
				order.addReceiver(bestSeller);
				order.setContent(targetHotelTitle);
				order.setConversationId("Hotel-booking");
				order.setReplyWith("order"+System.currentTimeMillis());
				myAgent.send(order);
				// Prepare the template to get the purchase order reply
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("Hotel-booking"),
						MessageTemplate.MatchInReplyTo(order.getReplyWith()));
				step = 3;
				break;
			case 3:      
				// Receive the purchase order reply
				reply = myAgent.receive(mt);
				if (reply != null) {
					// Purchase order reply received
					if (reply.getPerformative() == ACLMessage.INFORM) {
						// Purchase successful. We can terminate
						System.out.println(targetHotelTitle+" successfully booked from agent "+reply.getSender().getName());
						System.out.println("Price = "+bestHotelPrice);
						c = true;
					}
					else {
						System.out.println("Attempt failed: requested Hotel is full.");
					}

					step = 4;
				}
				else {
					block();
				}
				break;
			}        
		}

		public boolean done() {
			if (step == 2 && bestSeller == null) {
				System.out.println("Attempt failed: "+targetHotelTitle+" not available for booking");
			}
			return ((step == 2 && bestSeller == null) || step == 4);
		}
	}  // End of inner class RequestPerformer

	private class InformUserAgent extends CyclicBehaviour {
		public void action() {

			counter = counter +1;
			if  (a == true && b == true && c == true) {
				String bestPrice = Integer.toString(bestAutomobilePrice + bestFlightPrice + bestHotelPrice);
				
				ACLMessage reply = msg.createReply();
				reply.setPerformative(ACLMessage.INFORM);
				reply.setContent(bestPrice);
				System.out.println("Travel Package sold to agent "+msg.getSender().getName());
				myAgent.send(reply);
				myAgent.doDelete();
			}
			else if (counter == 15){

				ACLMessage reply = msg.createReply();

				reply.setPerformative(ACLMessage.REFUSE);
				reply.setContent("Travel Package cannot be purchased");
				myAgent.send(reply);
				myAgent.doDelete();

			}

			else {

				block();
			}	
		}
	}
}