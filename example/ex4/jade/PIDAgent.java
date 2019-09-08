
import jade.core.*;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.util.Logger;;
import redis.clients.jedis.Jedis;
import java.io.FileWriter;
import java.io.IOException;

/**
 * This agent implements a simple PID Agent that registers itself with the DF,
 * abtain signals from Redis  and send control signals back
 * If  a REQUEST message is received containing the string "level %f" within the content 
 * then it . 
 * 
 */
public class PIDAgent extends Agent {
    
    private Jedis jedis = new Jedis("localhost");
    private String sig_key = "sig";
    private String ctl_key = "ctl";
    private double level = 0.0;
    
    
    private double _dt=0.1;
    private  double _max;
    private  double _min;
    private  double _Kp=1.0;
    private  double _Kd=0.01;
    private  double _Ki=0.01;
    private  double _pre_error=0;
    private  double _integral=0;
    
    private Logger myLogger = Logger.getMyLogger(getClass().getName());
    
    private class PIDCycleBehaviour extends TickerBehaviour { //CyclicBehaviour {

	public double PID(double sig){
	    
	    double error = level - sig;
	    // Proportional term
	    double Pout = _Kp * error;
	    
	    // Integral term
	    _integral += error * _dt;
	    double Iout = _Ki * _integral;
	    
	    // Derivative term
	    double derivative = (error - _pre_error) / _dt;
	    double Dout = _Kd * derivative;
	    
	    _pre_error = error;
	    return Pout + Iout + Dout;
	    
	}
	    
	/*
	  public PIDCycleBehaviour(Agent a) {
	  super(a);
	  }
	*/
	
	public PIDCycleBehaviour(Agent a, int period) {
	    super(a,period);
	}
	
	//public void action() {
	public void onTick() {
	    
	    ACLMessage  msg = myAgent.receive();
	    if(msg != null){
		ACLMessage reply = msg.createReply();
		
		if(msg.getPerformative()== ACLMessage.REQUEST){
		    String content = msg.getContent();
		    if ((content != null) && (content.indexOf("level") != -1)){
					     
			String asd[] = content.split(" ");
			level = Double.parseDouble(asd[1]);
			
			myLogger.log(Logger.INFO, "Agent "+getLocalName()+" - Received level "+level);
			reply.setPerformative(ACLMessage.INFORM);
			reply.setContent("OK");
		    }
		    else{
			myLogger.log(Logger.INFO, "Agent "+getLocalName()+" - Unexpected request ["+content+"] received from "+msg.getSender().getLocalName());
			reply.setPerformative(ACLMessage.REFUSE);
			reply.setContent("( UnexpectedContent ("+content+"))");
		    }
		    
		} else {
		    myLogger.log(Logger.INFO, "Agent "+getLocalName()+" - Unexpected message ["+ACLMessage.getPerformative(msg.getPerformative())+"] received from "+msg.getSender().getLocalName());
		    reply.setPerformative(ACLMessage.NOT_UNDERSTOOD);
		    reply.setContent("( (Unexpected-act "+ACLMessage.getPerformative(msg.getPerformative())+") )");   
		}
		send(reply);
	    } else {
		block();
	    }	
	    // PID control
	    try{
		String str = jedis.get(sig_key);
		double sig = Double.parseDouble(str);
		double out = PID(sig);
		jedis.set(ctl_key,Double.toString(out));
		// myLogger.log(Logger.INFO, "Agent "+getLocalName()+" -Redis  get ["+sig + "] set ["+out+"]");
	    }catch(Exception e){
		myLogger.log(Logger.INFO, "Agent "+getLocalName()+" - Redis error ["+e);
		
	    }
	    
	}

	
    } // END of inner class PIDCycleBehaviour
    
    
    protected void setup() {
	// Registration with the DF 
	DFAgentDescription dfd = new DFAgentDescription();
	ServiceDescription sd = new ServiceDescription();   
	sd.setType("PIDAgent"); 
	sd.setName(getName());
	sd.setOwnership("");
	dfd.setName(getAID());
	dfd.addServices(sd);
	
	jedis.connect();
	jedis.set(ctl_key,Double.toString(level));
	jedis.set(sig_key,Double.toString(level));
	
	try {
	    DFService.register(this,dfd);
	    PIDCycleBehaviour PIDBehaviour = new  PIDCycleBehaviour(this,100);
	    addBehaviour(PIDBehaviour);
	} catch (FIPAException e) {
	    myLogger.log(Logger.SEVERE, "Agent "+getLocalName()+" - Cannot register with DF", e);
	    doDelete();
	}
    }
}
