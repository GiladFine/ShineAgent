package shineagent;
import geniusweb.issuevalue.*;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.logging.*;

import geniusweb.actions.Accept;
import geniusweb.actions.Action;
import geniusweb.actions.Comparison;
import geniusweb.actions.ElicitComparison;
import geniusweb.actions.Offer;
import geniusweb.actions.PartyId;
import geniusweb.bidspace.AllBidsList;
import geniusweb.party.Capabilities;
import geniusweb.party.DefaultParty;
import geniusweb.party.inform.ActionDone;
import geniusweb.party.inform.Finished;
import geniusweb.party.inform.Inform;
import geniusweb.party.inform.Settings;
import geniusweb.party.inform.YourTurn;
import geniusweb.profileconnection.ProfileConnectionFactory;
import geniusweb.profileconnection.ProfileInterface;
import geniusweb.progress.Progress;
import geniusweb.progress.ProgressRounds;
import tudelft.utilities.logging.Reporter;

/**
 * Shine Agent Main Class
 */
public class ShineParty extends DefaultParty {
	private static final double acceptQualityThreshold = 0.85;
	private static final double acceptQualityThresholdMin = 0.55;
	private static final double acceptDistanceThreshold = 0.15;
	private static final double newOfferDistanceThreshold = 0.15;
	private static final int numberOfTurnsToElicit = 1;
	private Bid lastReceivedBid = null; // we ignore all others
	private PartyId me;
	private final Random random = new Random();
	protected ProfileInterface profileint;
	private Progress progress;
	private SimpleLinearOrdering myEstimatedProfile = null;
	private SimpleLinearOrdering opponentEstimatedProfile = null;
	//private IssueCounter opponentIssueCounter = null;
	private Bid reservationBid = null;
	private int turnsWithElicitPassed = 0;

	
	public ShineParty() {
	}

	public ShineParty(Reporter reporter) {
		super(reporter); // for debugging
	}

	@Override
	public void notifyChange(Inform info) {
		try {
			if (info instanceof Settings) {
				Settings settings = (Settings) info;
				this.profileint = ProfileConnectionFactory
						.create(settings.getProfile().getURI(), getReporter());
				this.me = settings.getID();
				this.progress = settings.getProgress();
			} else if (info instanceof ActionDone) {
				Action otheract = ((ActionDone) info).getAction();
				if (otheract instanceof Offer) {
					lastReceivedBid = ((Offer) otheract).getBid();
				} else if (otheract instanceof Comparison) {
					myEstimatedProfile = myEstimatedProfile.with(
							((Comparison) otheract).getBid(),
							((Comparison) otheract).getWorse());
					myTurn();
				}
			} else if (info instanceof YourTurn) {
				myTurn();
			} else if (info instanceof Finished) {
				getReporter().log(Level.INFO, "Final ourcome:" + info);
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to handle info", e);
		}
	}

	@Override
	public Capabilities getCapabilities() {
		return new Capabilities(new HashSet<>(Arrays.asList("SHAOP")));
	}

	@Override
	public String getDescription() {
		return "Shine Agent :)";
	}

	/**
	 * Called when it's (still) our turn and we should take some action. Also
	 * Updates the progress if necessary.
	 */
	private void myTurn() throws IOException {
		Action action = null;
		if (myEstimatedProfile == null) {
			myEstimatedProfile = new SimpleLinearOrdering(
					profileint.getProfile());
			reservationBid = profileint.getProfile().getReservationBid();
		}
		
		if (lastReceivedBid != null) {
			processOpponentBid(lastReceivedBid);
			double lastBidQuality = getBidQuality(lastReceivedBid, myEstimatedProfile); 
			
			// then we do the action now, no need to ask user
			if (myEstimatedProfile.contains(lastReceivedBid))
			{
				if (lastBidQuality >= getBidQuality(reservationBid, myEstimatedProfile) && lastBidQuality >= getCurrentThreshold(acceptQualityThreshold, acceptQualityThresholdMin))
					action = new Accept(me, lastReceivedBid);
			}
			else {
				// we did not yet assess the received bid
				
				Bid closestBid = getClosestBid(myEstimatedProfile, lastReceivedBid);
				if(hammingDistance(closestBid, lastReceivedBid) < acceptDistanceThreshold)
				{
					if (getBidQuality(closestBid, myEstimatedProfile) >= getCurrentThreshold(acceptQualityThreshold, acceptQualityThresholdMin))
						action = new Accept(me, lastReceivedBid);
					else
					{
						action = newOffer();
					}
				}
				else
				{
					if(turnsWithElicitPassed >= numberOfTurnsToElicit)
					{
						action = new ElicitComparison(me, lastReceivedBid,
								myEstimatedProfile.getBids());
						
						turnsWithElicitPassed = 0;
					}
					else
					{
						++turnsWithElicitPassed;
						action = newOffer();
					}
						
				}
			}
			if (progress instanceof ProgressRounds) {
				progress = ((ProgressRounds) progress).advance();
			}
		}

		if (action == null)
			action = newOffer();
		
		getConnection().send(action);
	}

	private void processOpponentBid(Bid inputBid) throws IOException {
		
		if(opponentEstimatedProfile == null)
		{
			List<Bid> oneBidList = new ArrayList<>();
			oneBidList.add(inputBid);
			opponentEstimatedProfile = new SimpleLinearOrdering(profileint.getProfile().getDomain(), oneBidList);
		}
		
		/*if(opponentIssueCounter == null)
			opponentIssueCounter = new IssueCounter(profileint.getProfile().getDomain());*/
		
		//Insert new bid to opponent profile & Counter. This assumes all previous bids are worst, as the opponent learning it's own preferences as well.
		opponentEstimatedProfile = opponentEstimatedProfile.with(inputBid, opponentEstimatedProfile.getBids());
		//opponentIssueCounter.addElement(inputBid);
	}

	private Bid getClosestBid(SimpleLinearOrdering profile, Bid inputBid) throws IOException {
		Bid closestBid = null;
		double closestDist = 1.1;
		for(Bid currBid : profile.getBids())
		{
			if(closestDist > hammingDistance(currBid, inputBid))
			{
				closestBid = currBid;
				closestDist = hammingDistance(currBid, inputBid);
			}
		}
		return closestBid;
	}

	private Offer randomBid() throws IOException {
		AllBidsList bidspace = new AllBidsList(
				profileint.getProfile().getDomain());
		long i = random.nextInt(bidspace.size().intValue());
		Bid bid = bidspace.get(BigInteger.valueOf(i));
		return new Offer(me, bid);
	}
	
	private Offer newOffer() throws IOException {
		Bid selectedBid = null;
		double myBidQuality = 0.0;
		double opponentBidQuality = 0.0;
		double myBidDistanceToOpponent = 1.0;
		double myBidScore = 0.0;
		RandomCollection<Bid> weightedBids = new RandomCollection<>();
		for(Bid currBid : myEstimatedProfile.getBids())
		{
			myBidQuality = getBidQuality(currBid, myEstimatedProfile);
			
			//Drop bad offers
			if(myBidQuality < getBidQuality(reservationBid, myEstimatedProfile) || myBidQuality < getCurrentThreshold(acceptQualityThreshold, acceptQualityThresholdMin))
				continue;
			
			if(opponentEstimatedProfile != null)
			{
				//Calculate bid score using the opponent profile
				for(Bid currOpponentBid : opponentEstimatedProfile.getBids())
				{
					myBidDistanceToOpponent = hammingDistance(currBid, currOpponentBid);
					opponentBidQuality = getBidQuality(currOpponentBid, opponentEstimatedProfile);
					
					//Drop not similar bids
					if(myBidDistanceToOpponent > newOfferDistanceThreshold)
						continue;
					
					//Weight by opponent quality & distance
					myBidScore += (1 - myBidDistanceToOpponent) * opponentBidQuality;
				}
			}
			
			//Add a default score good bids with no information in opponent profile
			if(myBidScore <= 0.0)
				myBidScore = 0.1;
			
			//Weight by quality for the party
			myBidScore = myBidScore * myBidQuality;
			weightedBids.add(myBidScore, currBid);
			myBidScore = 0.0;
		}
		
		if(weightedBids.isEmpty())
		{
			return new Offer(me, myEstimatedProfile.getBids().get(0));
		}
		
		Bid weightedBid = weightedBids.next();
		AllBidsList allBids = new AllBidsList(myEstimatedProfile.getDomain());
		List<Bid> similarBids = new ArrayList<Bid>();
		for(Bid bid : allBids)
		{
			if(hammingDistance(bid, weightedBid) >= 0.2)
				continue;
			
			similarBids.add(bid);
		}
		
		return new Offer(me, similarBids.get(random.nextInt(similarBids.size())));
	}


	/**
	 * Calculate an index stating how good is this bid to this profile, between 0 (worst) to 1 (best)
	 * @param bid
	 * @param profile
	 * @return
	 */
	private double getBidQuality(Bid bid, SimpleLinearOrdering profile) {
		//TODO: Implement by threshold
		if (bid == null)
			return 0.0;
		
		return profile.getUtility(bid).doubleValue();
	}
	
	private double getMinMaxDistance(String issue) throws IOException
	{
		ValueSet vs1 = profileint.getProfile().getDomain().getValues(issue);
		NumberValue minValue = (NumberValue) vs1.get(0);
		NumberValue maxValue = (NumberValue) vs1.get(1);
		return (maxValue.getValue().doubleValue() - minValue.getValue().doubleValue());
	}
	
	/**
	 * Returns a score between 0 to 1 that determine how much the bids are similar
	 * 0 - identical, 1 - totally different
	 * @param bid1
	 * @param bid2
	 * @return
	 * @throws IOException
	 */
	private double hammingDistance(Bid bid1, Bid bid2) throws IOException {
		double similarityIndex = 0.0;
		for(Map.Entry<String, Value> valueEntry1 : bid1.getIssueValues().entrySet())
		{
			Value value1 = valueEntry1.getValue();
			Value value2 = bid2.getValue(valueEntry1.getKey());
			
			//Find out the type of bid value (range or discrete)
			if(value1 instanceof NumberValue)
			{
				double valueDist = Math.abs((((NumberValue)value1).getValue().doubleValue() - ((NumberValue)value2).getValue().doubleValue()));
				similarityIndex += (valueDist / getMinMaxDistance(valueEntry1.getKey()));
			} else {
				if(!valueEntry1.getValue().equals(value2))
				{
					similarityIndex++;
				}
			}
		}
		return (similarityIndex / bid1.getIssueValues().entrySet().size());
	}
	
	private double getCurrentThreshold(double threshold, double minValue)
	{
		if (!(progress instanceof ProgressRounds))
			return threshold;

		int currentRound = ((ProgressRounds) progress).getCurrentRound();
		int totalRounds = ((ProgressRounds) progress).getTotalRounds();
		if(totalRounds == 0)
			totalRounds++;
		return (1 - currentRound / totalRounds) * (threshold - minValue) + minValue;
	}

}
