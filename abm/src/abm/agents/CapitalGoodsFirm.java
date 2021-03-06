package abm.agents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

import org.apache.poi.util.SystemOutLogger;

import abm.MetaParameters;
import abm.Controller;
import abm.components.JobOffering;
import abm.components.NewMachine;
import abm.helpers.Utils;
import cern.jet.random.Binomial;
import cern.jet.random.Empirical;
import cern.jet.random.EmpiricalWalker;
import repast.simphony.random.RandomHelper;
import repast.simphony.util.collections.IndexedIterable;

public class CapitalGoodsFirm extends Firm {
	
	// State variables
	private ArrayList<NewMachine> catalog ;
	private double rd = 0 ;
	
	// Parameters
	private double upsilon =  0.5 ;
	
	public CapitalGoodsFirm(double income, double debt, double assets, int nEmployees) {
		super(income, debt, assets, nEmployees);
		this.catalog = new ArrayList<NewMachine>();
		calculateRD();
	}
	
	public ArrayList<NewMachine> getCatalog() {
		return catalog;
	}
	
	@Override
	public void planProduction() {
		
		super.planProduction();
		
		double oldRD = this.rd ;
		
		calculateRD();
		
		double diff = this.rd - oldRD ;
		
		if(diff > 0) {
			createJobOfferings(diff);
		} else if(diff < 0) {
			fireEmployees(-diff);
		} else {
			// nothing changes
		}
	}
	
	private void calculateRD() {
		this.rd = this.upsilon * this.income ;
	}
	
	private void createJobOfferings(double additionalRD) {
		
		ArrayList<EmployedConsumer> workers = laborMarket.getAdjacent(this) ;
		int limit = workers.size() - 1 ;

		while(additionalRD > 0) {
			
			double wage = this.meanWage ;
			
			if(limit != -1) {
				int inx = RandomHelper.nextIntFromTo(0, limit);
				wage = workers.get(inx).getWage();
			}
			else {
				// TODO It should never reach this point. If there are no workers, it should not sell (income = 0)
			}
			
			addJobOffering(new JobOffering(wage));
			additionalRD -= wage ;
		}
	}
	
	public void fireEmployees(double excessRD) {
		
		ArrayList<EmployedConsumer> workers = laborMarket.getAdjacent(this) ;
		
		while(excessRD > 0 && workers.size() > 0) {
			int inx = RandomHelper.nextIntFromTo(0, workers.size() - 1);
			EmployedConsumer worker = workers.get(inx) ;
			double wage = worker.getWage();
			fire(worker);
			workers.remove(inx) ;
			excessRD -= wage ;
			this.additionalPayroll -= wage ;
		}
	}
	
	public void sellMachine(NewMachine machine, ConsumptionGoodsFirm firm) {

		if(catalog.contains(machine)) {
			machine.increaseUnits(1);
			this.income += machine.getPrice() ;
			creditMarket.executeTransaction(this, firm, machine.getPrice());
		}
	}
	
	public void innovate() {
		
		double expn = 0.00001*MetaParameters.getInnExponent() ;
		double prob = 1 - Math.exp(-expn*this.rd);
		int inn = 1 ;
	
		if(prob != 1) {
			if(this.rd != 0) {
				Binomial binon = RandomHelper.createBinomial(1, prob) ;
				inn = binon.nextInt() ;
			}
			else {
				inn = 0 ;	
			} 
		}

		if(inn == 1) {
			
			boolean added = false ;
			
			while(!added) {
				int index = getSelectionFunction().nextInt() ;
				NewMachine machine = catalog.get(index);
				
				long newCap = (long) (machine.getCapacity()*(1+RandomHelper.nextDoubleFromTo(0,1)));
				double newWage = machine.getMaxWages()*(1-RandomHelper.nextDoubleFromTo(0,1));
				double newPrice =  machine.getPrice()*(1+RandomHelper.nextDoubleFromTo(0,1));
							
				NewMachine modified = new NewMachine(newCap, newWage, newPrice, 0);
				added = addMachine(modified);
			}
		}
	}
	
	private EmpiricalWalker getSelectionFunction() {
		
		double sum = 0 ;

		for(NewMachine machine : catalog) {		
			
			double units = machine.getUnits() ;
			sum += Math.exp(-units);
		}

		double[] probs = new double[catalog.size()];
		
		for(int i = 0; i < catalog.size(); i++) {
			
			NewMachine machine = catalog.get(i);
			double units = machine.getUnits();
			probs[i] = Math.exp(-units)/sum;
		}
		
		return RandomHelper.createEmpiricalWalker(probs, Empirical.NO_INTERPOLATION);
	}
	
	
	public boolean addMachine(NewMachine machine) {
		
		if(!catalog.contains(machine)) {
			catalog.add(machine);
			Collections.sort(catalog);
			return true ;
		}
		else {
			return false ;
		}
	}
	
	public double getRd() {
		return rd;
	}

	@Override 
	public String toString() {
		
		String strAgent = super.toString() ;
		Integer nMacs = this.catalog.size() ;
		String[][] fields = { { "Number of Machines", nMacs.toString()} } ;
		
	    return strAgent + Utils.getAgentDescriptor(fields, false) ;
	}
}
