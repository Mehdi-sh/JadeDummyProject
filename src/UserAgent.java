import java.io.IOException;

import content.TravelPackageObject;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;


public class UserAgent extends Agent {

	// The title of the Automobile to rent
	private String targetAutomobileTitle;
	// The title of the flight to buy
	private String targetFlightTitle;
	// The title of the Hotel to book
	private String targetHotelTitle;
	
	// The list of known travel agents
	private AID[] travelAgents;
	
	protected void setup() {
		// Printout a welcome message
		System.out.println("Hello! User-agent "+getAID().getName()+" is ready.");

		// Get the automobile, flight and hotel to buy as a start-up argument
		Object[] args = getArguments();
		if (args != null && args.length > 0) {
			targetAutomobileTitle = (String) args[0];
			targetFlightTitle = (String) args[1];
			targetHotelTitle = (String) args[2];
			System.out.println("Target automobile is "+targetAutomobileTitle+", Target flight is "+targetFlightTitle+", Target Hotel is "+targetHotelTitle);

			// Add a TickerBehaviour that schedules a request to travel agents every half a minute
			addBehaviour(new TickerBehaviour(this, 30000) {
				protected void onTick() {
					System.out.println("Trying to buy travel package including "+targetAutomobileTitle+", "+targetFlightTitle+", "+targetHotelTitle);
					// Update the list of travel agents
					DFAgentDescription template = new DFAgentDescription();
					ServiceDescription sd = new ServiceDescription();
					sd.setType("travel-broker");
					template.addServices(sd);
					try {
						DFAgentDescription[] result = DFService.search(myAgent, template); 
						System.out.println("Found the following travel agents:");
						travelAgents = new AID[result.length];
						for (int i = 0; i < result.length; ++i) {
							travelAgents[i] = result[i].getName();
							System.out.println(travelAgents[i].getName());
						}
					}
					catch (FIPAException fe) {
						fe.printStackTrace();
					}

					// Perform the request
					myAgent.addBehaviour(new packageRequestPerformer());
				}
			} );
		}
		else {
			// Make the agent terminate
			System.out.println("No target travel package specified");
			doDelete();
		}
	}
	
	private class packageRequestPerformer extends Behaviour {
		private AID bestSeller; // The agent who provides the best offer 
		private int bestPrice;  // The best offered price
		private int repliesCnt = 0; // The counter of replies from seller agents
		private MessageTemplate mt; // The template to receive replies
		private int step = 0;

		public void action() {
			switch (step) {
			case 0:
				// Send the cfp to all sellers
				ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
				for (int i = 0; i < travelAgents.length; ++i) {
					cfp.addReceiver(travelAgents[i]);
				} 
				try {
					cfp.setContentObject(new TravelPackageObject(targetAutomobileTitle,targetFlightTitle,targetHotelTitle));
				} catch (IOException e) {
					
					e.printStackTrace();
				}
				cfp.setConversationId("travel-package-requesting");
				cfp.setReplyWith("cfp"+System.currentTimeMillis()); // Unique value
				myAgent.send(cfp);
				// Prepare the template to get proposals
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("travel-package-requesting"),
						MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
				step = 1;
				break;
			case 1:      
				// Receive the purchase order reply
				ACLMessage reply = myAgent.receive(mt);
				if (reply != null) {
					// Purchase order reply received
					if (reply.getPerformative() == ACLMessage.INFORM) {
						// Purchase successful. We can terminate
						System.out.println("Travel Package successfully bought from agent!!!!! "+reply.getSender().getName());
						System.out.println("Price = "+reply.getContent());
						myAgent.doDelete();
					}
					else if (reply.getPerformative() == ACLMessage.REFUSE) {
						System.out.println("Attempt failed: travel package cannot be purchased!!!!!");
						myAgent.doDelete();
					}
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
	}	
}

