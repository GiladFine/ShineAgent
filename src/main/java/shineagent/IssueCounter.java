package shineagent;

import java.util.HashMap;
import java.util.Map;

import geniusweb.issuevalue.Value;
import geniusweb.issuevalue.ValueSet;
import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.Domain;

public class IssueCounter extends HashMap<String, HashMap<Value, Integer>> {
		private Domain domain;
		public IssueCounter(Domain domain) {
			super();
			this.domain = domain;
			for (String issue : domain.getIssues()) {
				ValueSet vs = domain.getValues(issue);
				HashMap<Value, Integer> issueMap = new HashMap<>();
				for(Value value : vs)
				{
					issueMap.put(value, 0);
				}	
				this.put(issue, issueMap);
			}
		}
		
		public void addElement(Bid inputBid) {
			for (String issue : domain.getIssues()) {
				Value value = inputBid.getValue(issue);
				if(value == null)
					continue;
				int newCount = this.get(issue).get(value) + 1;
				this.get(issue).put(value, newCount);
			}
		}
}
