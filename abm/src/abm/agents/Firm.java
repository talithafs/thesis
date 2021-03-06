package abm.agents;

import java.util.ArrayList;
import java.util.HashMap;

import abm.components.JobOffering;
import abm.helpers.Utils;
import abm.helpers.Constants.Keys;
import abm.links.Job;
import abm.markets.CapitalGoodsMarket;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ISchedule;
import repast.simphony.engine.schedule.Schedule;

public class Firm extends NonFinancialAgent {
	
	// State variables
	private int nEmployees = -1 ;
	private double payroll = 0 ;
	protected double income = -1 ;
	protected double profit ;
	protected double meanWage = 0 ;
	protected ArrayList<JobOffering> offerings = null ;

	// Auxiliary variables
	protected double additionalPayroll = 0; 
	protected double remainingWages = 0 ;
	protected double neededForLabor = 0 ;
	private double fromAssets = 0 ;
	
	// Parameters
	protected double alpha =  0.5 ;
	
	// Markets
	protected final static CapitalGoodsMarket capitalGoodsMarket = CapitalGoodsMarket.getInstance() ;

	public Firm(double income, double debt, double assets, int nEmployees) {
		super(assets,debt);
		this.income = income ;
		this.nEmployees = nEmployees ;
	}
	
	public void planProduction() { 
		calculateProfit();
	}
	
	public void calculateProfit() {
		
		this.profit = this.income - this.getPayroll() ;
		this.payroll += this.additionalPayroll ;
		this.additionalPayroll = 0 ;
	}
	
	public void calculateNeededCredit() {
		
		double absProfit = Math.abs(this.profit);
		
		boolean cond1 = (this.profit >= 0);
		boolean cond2 = this.getNetWorth() + this.profit <= 0 ;
		
		if(cond1 || cond2) {
			this.neededCredit = 0 ;
			
			if(cond1) {
				this.fromAssets = this.income ;
			} 
			else { //profit < 0 
				this.fromAssets = this.income + absProfit ;
			}
		} 
		else { // profit < 0 && netWorth + profit > 0 
			this.neededCredit = (1-this.alpha)*absProfit;
			this.fromAssets = this.income + this.alpha * absProfit ;
		}
		
		this.neededForLabor = this.neededCredit ;
	}
	
	public void receiveGrantedCredit() {
		
		this.grantedCredit = 0 ;
		Bank bank = creditMarket.getBankWithDeposits(this);
		bank.deposit(this, this.income);
		this.income = 0 ;
		
		super.receiveGrantedCredit();
	}
	

	public void postJobOfferings() {
		
		double grantedForLabor = 0 ;
		
		if(this.neededForLabor != 0) {
			if(this.grantedCredit > this.neededForLabor) {
				grantedForLabor = this.neededForLabor ;
			}
			else {
				grantedForLabor = this.grantedCredit ;
			}
		}

		double resources = this.fromAssets +  grantedForLabor ;
		
		if(this.offerings != null && !this.offerings.isEmpty()) {
			while(this.additionalPayroll > resources) {
				
				JobOffering offering = this.offerings.remove(0);
				this.additionalPayroll -= offering.getWage() ;
			}
			
			if(!this.offerings.isEmpty()) {
				laborMarket.postJobOfferings(this, this.offerings);	
			}
			
			this.additionalPayroll = 0 ;
		}
	}
	
	public ArrayList<JobOffering> copyOfferings(){
		
		ArrayList<JobOffering> offerings = new ArrayList<JobOffering>();
		
		if(this.offerings != null) {
			for(JobOffering offering : this.offerings) {
				offerings.add(offering);
			}
		}
		return offerings ;
	}
	
	public void payEmployees() {
		
		ArrayList<EmployedConsumer> employees = laborMarket.getAdjacent(this);
		Bank bank = creditMarket.getBankWithDeposits(this);
		double remaining = 0 ;
		
		for(EmployedConsumer employee : employees) {
			double wage = employee.getWage();
			if(this.getAssets() - wage >= 0) {
				bank.withdraw(this, wage);
				employee.receivePayment(wage);
			}
			else {
				employee.receivePayment(0);
				remaining += wage ;
			}
		}
		
		this.remainingWages = remaining ;
	}

	
	public void initJob(EmployedConsumer consumer) {
		
		double wage = ((EmployedConsumer) consumer).getWage();
		laborMarket.addEdge(new Job(consumer, this, false, wage));
		this.payroll += wage ;
	}
	
	public EmployedConsumer hire(Consumer consumer, JobOffering offering) {
		
		HashMap<Integer,Integer> map = new HashMap<Integer,Integer>();
		
		double wage = offering.getWage();
		this.additionalPayroll += wage ;
		
		if(consumer instanceof UnemployedConsumer) {
			consumer = Consumer.changeStatus((UnemployedConsumer) consumer,  wage);
		} 

		laborMarket.addEdge(new Job(this, consumer, false,  wage));
		
		double sum  = ((this.meanWage)*this.nEmployees) + wage ;
		this.nEmployees ++ ;
		this.meanWage = sum / this.nEmployees ;
		
		offering.setTaken(true);
		
		return (EmployedConsumer) consumer ;
	}
	
	public void fire(EmployedConsumer emp) {
		
		laborMarket.removeEdge(this, emp);
		double wage = emp.getWage() ;
		
		if(this.getAssets() >= wage) {
			Bank bank = creditMarket.getBankWithDeposits(this);
			bank.withdraw(this, wage);
			emp.receivePayment(wage);
		}
		
		double sum  = ((this.meanWage)*this.nEmployees) - wage ;
		this.nEmployees -- ;
		this.meanWage = sum / this.nEmployees ;
		
		if(this.meanWage <= 0) {
			this.meanWage = Government.getInstance().getParam(Keys.MIN_WAGE) ;
		}
	}
	
	public void addJobOffering(JobOffering offering) {
		
		if(this.offerings == null) {
			this.offerings = new ArrayList<JobOffering>();
		}
		
		offerings.add(offering);
		this.additionalPayroll += offering.getWage() ;
	}
	
	public int getNEmployees() {
		return nEmployees;
	}

	public void setNEmployees(int nEmployees) {
		
		if(this.nEmployees == -1) {
			this.nEmployees = nEmployees;
		}
	}
	
	public double getPayroll() {
		return payroll;
	}
	
	public void setPayroll(double payroll) {
		this.payroll = payroll;
	}

	public double getIncome() {
		return income;
	}

	public void setIncome(double income) {
			this.income = income;
	}
	
	public double getAdditionalPayroll() {
		return additionalPayroll ;
	}
	
	public ArrayList<JobOffering> getJobOfferings() {
		return this.offerings ;
	}

	public double getProfit() {
		return profit;
	}

	public double getGrantedCredit() {
		return grantedCredit;
	}

	public double getRemainingWages() {
		return remainingWages;
	}

	public double getNeededForLabor() {
		return neededForLabor;
	}

	public double getAlpha() {
		return alpha;
	}

	public double getFromAssets() {
		return fromAssets;
	}

	@Override
	public String toString() {
		
		String strAgent = super.toString() ;
		
		Integer nEmps = this.nEmployees;
		Double income = this.income ;

		String[][] fields = { { "Number of Employees", nEmps.toString()}, 
							{"Income", income.toString()} } ;
		
	    return strAgent + Utils.getAgentDescriptor(fields, false) ;
	}
}
