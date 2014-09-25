package cloudos.appstore.dao;

import cloudos.appstore.model.AppPrice;
import org.cobbzilla.wizard.dao.AbstractCRUDDAO;
import org.hibernate.criterion.Restrictions;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AppPriceDAO extends AbstractCRUDDAO<AppPrice> {

    public List<AppPrice> findByApp(String uuid) {
        return list(criteria().add(Restrictions.eq("cloudApp", uuid)));
    }

    public AppPrice findByAppAndCurrency (String app, String isoCurrency) {
        return uniqueResult(criteria().add(
                Restrictions.and(
                        Restrictions.eq("cloudApp", app),
                        Restrictions.eq("isoCurrency", isoCurrency)
                )));
    }

}
