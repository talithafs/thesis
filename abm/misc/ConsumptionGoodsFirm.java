package abm.agents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.TreeSet;

import abm.Calibrable;
import abm.components.JobOffering;
import abm.components.MachineOrder;
import abm.components.NewMachine;
import abm.components.UsedMachine;
import abm.helpers.Constants.Keys;
import abm.helpers.Utils;
import abm.markets.ConsumptionGoodsMarket;
import abm.markets.CreditMarket;
import cern.jet.random.Binomial;
import repast.simphony.random.RandomHelper;
import repast.simphony.util.collections.Pair;

public class ConsumptionGoodsFirm extends Firm {
	
	// State variables	
	private ArrayList<UsedMachine> machines ;
	private ArrayList<Long> sales ;
	private ArrayList<Long> production ;
	private ArrayList<MachineOrder> investments ;
	private long supply = 0 ; 
	private long installedCap = 0;
	private long usedCap = 0;
	private long inventory = 0 ;
	private double price = -1;
	
	// Parameters
	private double beta =  0.1 ;
	private double kappa =  0.001 ;
	private double nu =  0.95 ;
	
	// Auxiliary variables
	private long eDemand = 0 ;
	private long dProduction = 0 ;
	private double dInvestment = 0 ;
	private long currentSales =  0 ; 
	private double neededForInvestment = 0 ;
	private Pair<UsedMachine,NewMachine> replacement ;
	
	// Markets
	protected final static ConsumptionGoodsMarket consumptionGoodsMarket = ConsumptionGoodsMarket.getInstance() ;
	
	public ConsumptionGoodsFirm(double income, double price, double debt, double assets, int nEmployees) {
		
		super(income, debt, assets, nEmployees);
		
		this.machines = new ArrayList<UsedMachine>();
		this.sales = new ArrayList<Long>();
		this.production = new ArrayList<Long>();
		
		this.price = price ;
		double qty = this.income / price ;
		this.currentSales = (long) qty ;
		this.production.add((long) ((1-this.beta)*qty));
		this.inventory = (long) (this.beta*qty) ;
		this.supply = this.inventory + getLastProduction();
	}
	
	public void init() {
		
		long diff = getLastProduction() - this.usedCap ;
		
		if(diff > 0) {
			long lastProd = this.production.remove(0);
			this.production.add(lastProd - diff) ;
			this.inventory = (long) (2*this.beta*(getCurrentSales())) + diff;
		}
		else if (diff < 0){
			System.out.println("Never should have reached this point.");
		}
	}
	
	public int optimumInventory() {
		return (int) (this.beta*this.eDemand) ;
	}
	
	public void planProduction() {
		
		super.planProduction(); 
		
		this.sales.add(this.currentSales);
		this.currentSales = 0 ;
		
		this.eDemand = calculateExpectedDemand();
		this.dProduction = calculateDesiredProduction() ;
		this.price = calculatePrice();
		
		// TODO Treat when dProduction is zero!

		if(dProduction < usedCap) {
			fireEmployees();
		} 
		else if(dProduction > usedCap){
			
			this.investments = new ArrayList<MachineOrder>() ;
			
			if(dProduction > installedCap) {
				createJobOfferings(false);
				calculateExpansionInvestment();
			} 
			else {
				createJobOfferings(true);
			}
			calculateReplacementInvestment();
		} 
	}
	
	private long calculateExpectedDemand() {
		return getLastSales(); 
	}
	
	
	private long calculateDesiredProduction() {	
		long prod = this.eDemand + optimumInventory() - this.inventory;
		
		// TODO O que fazer quando a diferença é muito pequena? 
		
//		if(Math.abs(prod - usedCap) == 1) {
//			prod = usedCap ;
//		}		
				
		return prod ;
	}
	
	private double calculatePrice() {
		double var = calculateInventory();
		return this.price + this.kappa*(-var);
	}
	
	private double calculateInventory() {
		
		long oldInventory = this.inventory ;
		long newInventory = this.supply - this.getLastSales() ;
		this.inventory = newInventory ; 
		
		if(oldInventory == newInventory) {
			return 0 ;
		} 
		else {

			if(oldInventory == 0) {
				oldInventory = 1 ;
			}
			return (newInventory - oldInventory)/oldInventory ;
		}
	}
	
	public void fireEmployees() {
		
		long uCap = this.usedCap ;
		this.additionalPayroll = 0 ;
		ids = new ArrayList<Integer>();

		for(UsedMachine machine : machines) {
			
			while(uCap > dProduction && !machine.getOperators().isEmpty()) {
				
				EmployedConsumer operator = machine.getOperators().get(0);

				long oldCap = machine.getUsedCapacity() ;
				machine.removeOperator(operator);
				long newCap = machine.getUsedCapacity() ;
				
				uCap -= (oldCap - newCap) ;

				this.additionalPayroll -= operator.getWage() ;
				fire(operator);
				
				if(ids.contains(operator.getId())){
					System.out.println("Con firm " + getId() + " trying to fire " + operator.getId() + " twice.");
				} else {
					ids.add(operator.getId());
				}
			}
		}	
		
		this.usedCap = uCap ;
	}
	
	private void createJobOfferings(boolean proportional) {
		
		double minWage = Government.getInstance().getParam(Keys.MIN_WAGE);
		int size = this.machines.size();
		long extra = dProduction - usedCap; 

		this.offerings = new ArrayList<JobOffering>();
		
		for(int i = size-1; i >= 0; i--) {
			
			UsedMachine machine = this.machines.get(i);
			double difWage = machine.getMaxWages() - machine.getUsedWages() ;
			
			if(proportional) {
				
				double unused = machine.getCapacity() - machine.getUsedCapacity() ;
				double wage = 0 ; 
				
				if(extra <= unused) {
					wage = (extra/machine.getEfficiency())*minWage ;
					addJobOffering(new JobOffering(wage,machine));
					break ;
				} 
				else { // extra > unused
					extra -= unused ;
					wage = difWage*minWage ;
					addJobOffering(new JobOffering(wage,machine));
				}
			}
			else { // not proportional
				double wage = difWage*minWage ;
				addJobOffering(new JobOffering(wage,machine));
			}
		}
	}
	
	private void calculateExpansionInvestment() {
		
		Government gov = Government.getInstance() ;
		int n = (int) ((this.getNEmployees() / gov.getParam(Keys.N_CONSUMERS))*gov.getParam(Keys.N_CAP_FIRMS));
		
		ArrayList<CapitalGoodsFirm> firms = capitalGoodsMarket.getAdjacent(this);
		ArrayList<CapitalGoodsFirm> randomFirms = capitalGoodsMarket.getRandomFirms(this, n);
		
		firms.addAll(randomFirms);
		
		ArrayList<MachineOrder> investments = new ArrayList<MachineOrder>();
		
		double diff = dProduction - installedCap  ;
		double maxEff = Double.MIN_VALUE ;
		
		MachineOrder preOrder = null ;
		MachineOrder best = null ;
		
		for(CapitalGoodsFirm firm : firms) {
			
			ArrayList<NewMachine> catalog = firm.getCatalog() ;
			
			for(NewMachine machine : catalog) {
				
				if(machine.getEfficiency() > maxEff) {
					
					best = new MachineOrder(machine, (CapitalGoodsFirm) firm, 1);
					maxEff = machine.getEfficiency() ;
					
					if(machine.getCapacity() >= diff) {
						preOrder = new MachineOrder(machine, (CapitalGoodsFirm) firm, 1);
						break ;
					}
				}
			}
		}
		
		if(preOrder == null) {
			
			while(diff > 0) {
				NewMachine mac = best.getMachine() ; 
				diff -= mac.getCapacity() ;
				best.increaseQuantity(1);
			}
			
			investments.add(best);
		}
		else {
			investments.add(preOrder);
		}
		
		this.investments = investments ;
	}
	
	private void calculateReplacementInvestment() {
		
		if(this.getNetWorth() > 0) {
			
			double prob = 1 - Math.exp(-Calibrable.getRepExponent()*this.getNetWorth());
			int replace = 1 ;
			
			if(prob != 1) {
				Binomial binon = RandomHelper.createBinomial(1, prob) ;
				replace = binon.nextInt() ;
			}

			if(replace == 1) {
				
				UsedMachine oldMachine = machines.get(0);
				ArrayList<CapitalGoodsFirm> adjFirms = capitalGoodsMarket.getAdjacent(this);
				
				double efficiency = oldMachine.getEfficiency() ;
				double minPrice = Double.MAX_VALUE ;
				NewMachine chosenMachine = null ;
				CapitalGoodsFirm chosenFirm = null ; 
				
				for(CapitalGoodsFirm firm : adjFirms) {
					
					ArrayList<NewMachine> newMachines = firm.getCatalog() ;
					
					for(NewMachine newMachine : newMachines) {
						
						if(newMachine.getEfficiency() > efficiency) {
							if(newMachine.getPrice() < minPrice) {
								chosenMachine = newMachine ;
								chosenFirm = firm ;
							}
						}
					}
				}
				
				if(chosenMachine != null) {
					
					replacement = new Pair<UsedMachine,NewMachine>(oldMachine, chosenMachine);
					MachineOrder order = new MachineOrder(chosenMachine, chosenFirm, 1);
					
					if(!investments.contains(order)) {
						investments.add(order);
					}
					else {
						int inx = investments.indexOf(order);
						investments.get(inx).increaseQuantity(1);
					}	
				}	
			}
		}
	}
	
	@Override
	public void calculateNeededCredit() {
		
		super.calculateNeededCredit(); 	
		this.neededForInvestment = 0 ;
		
		if(investments != null && investments.size() != 0) {
			
			dInvestment = 0 ;
			
			for(MachineOrder order : investments) {
				dInvestment += order.getMachine().getPrice() * order.getQuantity() ;
			}
			
			if(this.getNetWorth() + this.profit > (1-this.alpha)*dInvestment){
				this.neededForInvestment = (1-this.alpha)*dInvestment ;
				this.neededCredit += this.neededForInvestment ;
			}
		}
	}	
	

	public EmployedConsumer hire(Consumer consumer, JobOffering offering) {
		
		EmployedConsumer con = super.hire(consumer, offering);
		UsedMachine machine = offering.getMachine() ;
		
		this.usedCap -= machine.getUsedCapacity() ;
		this.installedCap -= machine.getCapacity() ; 
		
		machine.addOperator(con);
		
		this.usedCap += machine.getUsedCapacity() ;
		this.installedCap += machine.getCapacity() ; 
		
		return con ;
	}
	
	public void buyMachines() {
		
		ArrayList<MachineOrder> orders = new ArrayList<MachineOrder>();
		
		if(this.getAssets() > 0 && this.investments != null) {
				
			Collections.sort(investments);
			int i = investments.size() - 1 ;
			double assets = this.getAssets() ;
			
			while(i >= 0) {
				double price = investments.get(i).getMachine().getPrice() ;
				
				if(assets - price >= 0) {
					orders.add(investments.get(i));
					assets -= price ;
				}
				i-- ;
			}
		}
		
		this.investments = orders ;
		capitalGoodsMarket.executeSales(this, this.investments);
	}
	
	public void produceGoods() {
		
		long production = 0 ;
		
		if(this.remainingWages == 0) {
			production = this.usedCap ;	
		}
		else {
			double minWage = Government.getInstance().getParam(Keys.MIN_WAGE);
			
			for(UsedMachine machine : machines) {
				
				ArrayList<EmployedConsumer> operators = machine.getOperators() ;
				
				for(EmployedConsumer operator : operators) {
					
					if(operator.getPayment() == 0) {
						production += this.nu * (operator.getWage()/minWage) * machine.getEfficiency();
					}
					else {
						production +=  (operator.getWage()/minWage) * machine.getEfficiency() ;
					}
				}
			}
		}
		
		this.production.add(production);
		this.supply = production + this.inventory ;
		this.income = 0 ;
	}
	
	public void sellGoods() {
		
		long supply = this.supply ;
		supply -= this.currentSales ; 

		if(supply > 0) { 
			
			boolean soldOut = false ;
			boolean isEmpty = consumptionGoodsMarket.isQueueEmpty(this);
			int sold = 0 ;
			
			while(!soldOut && !isEmpty) {
				
				Consumer con = consumptionGoodsMarket.pollQueue(this);
				long conDemand = con.getDemand() ;
				
				double qty = 0 ;
				if(conDemand <= supply) {
					qty = con.decideToBuy(conDemand);
				}
				else { // conDemand > supply
					qty = con.decideToBuy(supply);
				}
				
				if(qty > 0) {
					supply -= qty ;
					sold += qty ;
					creditMarket.executeTransaction(this, con, qty*this.price);
					consumptionGoodsMarket.addEdge(this, con);
					this.income += qty*this.price ;
				}
				
				if(supply == 0) {
					soldOut = true ;
				}
				
				isEmpty = consumptionGoodsMarket.isQueueEmpty(this);
				
			}
			
			this.currentSales += sold ;
		}
	}
	
	public void receiveMachines() {
		
		for(MachineOrder order : investments) {
			NewMachine newMachine = order.getMachine() ;
			UsedMachine usedMachine = newMachine.toUsedMachine() ;
			
			if(replacement.getSecond().equals(newMachine)){
				
				UsedMachine oldMachine = replacement.getFirst() ;
				ArrayList<EmployedConsumer> operators = oldMachine.getOperators();
				usedMachine.addOperators(operators);
				removeMachine(oldMachine);
			} 
			
			addMachine(usedMachine);
		}
	}

	public void addMachine(UsedMachine machine) {
		machines.add(machine);
		this.usedCap += machine.getUsedCapacity() ;
		this.installedCap += machine.getCapacity() ; 
		Collections.sort(machines);
	}
	
	public void removeMachine(UsedMachine machine) {
		machines.remove(machine);
		this.usedCap -= machine.getUsedCapacity() ;
		this.installedCap -= machine.getCapacity() ;
	}

	public long getLastSales() {
		return sales.get(sales.size()-1) ;	
	}
	
	public long getLastProduction() {
		return production.get(production.size()-1) ;
	}
	
	public long getLastSupply() {
		return this.supply ;
	}
	
	public long getCurrentSales() {
		return currentSales;
	}
	
	public void setCurrentSales(long quantity) {
		this.income = quantity * this.price ;
		this.currentSales = quantity ; 
	}

	public void setLastSupply(long production, long inventory) {
		this.production.add(production);
		this.inventory = inventory ;
		this.supply = production + inventory ;
	}
	
	public double getPrice() {
		return this.price ;
	}
	
	public ArrayList<UsedMachine> getMachines(){
		return machines ;
	}

	public long getInstalledCapacity() {
		return installedCap;
	}

	public long getUsedCapacity() {
		return usedCap;
	}
	
	public long getInventory() {
		return inventory;
	}
	
	public double getBeta() {
		return this.beta ;
	}
	
	public long getExpectedDemand() {
		return eDemand ;
	}
	
	public long getDesiredProduction() {
		return dProduction ;
	}

	public double getNu() {
		return nu;
	}

	public ArrayList<MachineOrder> getInvestments(){
		return this.investments ;
	}

	public void setInventory(long inventory) {
		this.inventory = inventory;
	}

	public Pair<UsedMachine, NewMachine> getReplacement() {
		return replacement;
	}
	
	public double getNeededForInvestment() {
		return neededForInvestment;
	}

	@Override 
	public String toString() {
		
		String strAgent = super.toString() ;
		Integer nMacs = this.machines.size() ;
		Long lastSales = getLastSales();
		Double price = this.price ;
		Long usedCap = this.usedCap ;
		
		String[][] fields = { { "Number of Machines", nMacs.toString()},
							  { "Last sales", lastSales.toString()},
							  { "Price", price.toString()},
							  { "Used Capacity", usedCap.toString()}} ;
		
	    return strAgent + Utils.getAgentDescriptor(fields, false) ;
	}
	
	
}
